/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.mccmp;

import codechicken.diffpatch.cli.DiffOperation;
import juuxel.mccmp.data.GlobalManifest;
import juuxel.mccmp.data.MinecraftMetadata;
import juuxel.mccmp.data.VersionManifest;
import juuxel.mccmp.data.YarnVersion;
import net.fabricmc.tinyremapper.FileSystemReference;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import picocli.CommandLine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@CommandLine.Command(name = "mccmp", mixinStandardHelpOptions = true)
public final class Mccmp implements Runnable {
    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final Pattern SYNTHETIC_LV_NAME_PATTERN = Pattern.compile("^☃|\\$\\$[0-9]+$");

    @CommandLine.Parameters(index = "0", arity = "1")
    public String fromVersion;

    @CommandLine.Parameters(index = "1", arity = "1")
    public String toVersion;

    @CommandLine.Option(names = {"-o", "--output"})
    public Path outputPath = Path.of(".");

    @Override
    public void run() {
        Path libraryDir = outputPath.resolve("libraries");
        var metadata = Download.json(MANIFEST_URL, GlobalManifest.class)
            .thenCompose(manifest -> {
                String fromUrl = null;
                String toUrl = null;

                for (GlobalManifest.Version version : manifest.versions()) {
                    if (version.id().equals(fromVersion)) {
                        fromUrl = version.url();
                    } else if (version.id().equals(toVersion)) {
                        toUrl = version.url();
                    }
                }

                Objects.requireNonNull(fromUrl, "could not find 'from' version");
                Objects.requireNonNull(toUrl, "could not find 'to' version");

                var fromManifestFuture = Download.json(fromUrl, VersionManifest.class);
                var toManifestFuture = Download.json(toUrl, VersionManifest.class);

                return fromManifestFuture.thenCompose(fromManifest -> toManifestFuture.thenCompose(toManifest -> {
                    Set<VersionManifest.Library> allLibraries = new HashSet<>(fromManifest.libraries());
                    allLibraries.addAll(toManifest.libraries());
                    var fromLibrary = libraryForMinecraft(fromManifest);
                    var toLibrary = libraryForMinecraft(toManifest);
                    allLibraries.add(fromLibrary);
                    allLibraries.add(toLibrary);

                    var libraryFuture = CompletableFuture.allOf(
                            allLibraries.stream()
                                .map(library -> {
                                    Path path = libraryDir.resolve(library.downloads().artifact().path());
                                    try {
                                        Files.createDirectories(path.getParent());
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                    return Download.file(path, library.downloads().artifact().url());
                                })
                                .toArray(CompletableFuture[]::new)
                        );
                    var metadataFuture = resolveMetadata(libraryDir, fromManifest)
                        .thenCombine(resolveMetadata(libraryDir, toManifest), Pair::new);
                    return libraryFuture.thenCombine(metadataFuture, (unused, pair) -> pair);
                }));
            })
            .join();

        Pair<Path, Path> remappedJars;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = CompletableFuture.supplyAsync(() -> remap(libraryDir, metadata.first()), executor);
            var second = CompletableFuture.supplyAsync(() -> remap(libraryDir, metadata.second()), executor);
            remappedJars = first.thenCombine(second, Pair::new).join();
        }

        decompile(libraryDir, remappedJars.first(), metadata.first().manifest());
        decompile(libraryDir, remappedJars.second(), metadata.second().manifest());

        Path diffDir = outputPath.resolve("diffs");
        Path diffJarPath = diffDir.resolve("%s-%s.jar".formatted(fromVersion, toVersion));
        Path diffDirPath = diffDir.resolve("%s-%s".formatted(fromVersion, toVersion));

        try {
            diff(
                getSourcePath(remappedJars.first()),
                getSourcePath(remappedJars.second()),
                diffJarPath,
                diffDirPath
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path remap(Path libraryDir, MinecraftMetadata metadata) {
        var remappedJarPath = metadata.gameJar()
            .resolveSibling("minecraft-%s-%s".formatted(metadata.id(), metadata.mappingsJar().getFileName()));
        if (Files.exists(remappedJarPath)) return remappedJarPath;

        System.out.println(":remapping " + metadata.id() + " with " + metadata.mappingsJar().getFileName());

        try {
            var mappingsPath = Mappings.extractMappings(metadata.gameJar(), metadata.mappingsJar());
            var mappingProvider = TinyUtils.createTinyMappingProvider(mappingsPath, "official", "named");

            TinyRemapper remapper = TinyRemapper.newRemapper()
                .threads(Runtime.getRuntime().availableProcessors() / 2)
                .withMappings(mappingProvider)
                .renameInvalidLocals(true)
                .invalidLvNamePattern(SYNTHETIC_LV_NAME_PATTERN)
                .build();

            try (var outputConsumer = new OutputConsumerPath.Builder(remappedJarPath).build()) {
                outputConsumer.addNonClassFiles(metadata.gameJar(), NonClassCopyMode.SKIP_META_INF, remapper);

                Path[] libraries = metadata.manifest()
                    .libraries()
                    .stream()
                    .map(library -> libraryDir.resolve(library.downloads().artifact().path()))
                    .toArray(Path[]::new);

                remapper.readClassPath(libraries);
                remapper.readInputs(metadata.gameJar());
                remapper.apply(outputConsumer);
            } finally {
                remapper.finish();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return remappedJarPath;
    }

    private static CompletableFuture<MinecraftMetadata> resolveMetadata(Path libraryDir, VersionManifest manifest) {
        return Download.json("https://meta.fabricmc.net/v2/versions/yarn/" + manifest.id(), new TypeToken<List<YarnVersion>>() {})
            .thenCompose(yarnVersions -> {
                String yarnVersion = yarnVersions.get(0).version();
                var yarnMergedV2 = new DependencyCoordinates("net.fabricmc", "yarn", yarnVersion, "mergedv2");
                var yarnV1 = new DependencyCoordinates("net.fabricmc", "yarn", yarnVersion);

                try {
                    Files.createDirectories(libraryDir.resolve(yarnMergedV2.toUrlPart()).getParent());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                List<Supplier<CompletableFuture<Path>>> downloads = List.of(
                    () -> Download.file(libraryDir.resolve(yarnMergedV2.toUrlPart()), yarnMergedV2.toFabricMavenUrl()),
                    () -> Download.file(libraryDir.resolve(yarnV1.toUrlPart()), yarnV1.toFabricMavenUrl())
                );

                return Futures.runFirstSuccessful(
                    t -> t instanceof Download.StatusCodeException e && e.statusCode() == 404,
                    downloads
                ).thenApply(yarnJar -> {
                    var mcPathStr = libraryForMinecraft(manifest).downloads().artifact().path();
                    var mcPath = libraryDir.resolve(mcPathStr);
                    return new MinecraftMetadata(manifest.id(), manifest, mcPath, yarnJar);
                });
            });
    }

    private void decompile(Path libraryDir, Path gameJar, VersionManifest manifest) {
        List<String> args = new ArrayList<>();

        for (VersionManifest.Library library : manifest.libraries()) {
            var path = libraryDir.resolve(library.downloads().artifact().path()).toAbsolutePath().toString();
            args.add("-e=" + path);
        }

        args.add(gameJar.toAbsolutePath().toString());
        args.add(getSourcePath(gameJar).toAbsolutePath().toString());
        System.out.println(":decompiling " + manifest.id());
        ConsoleDecompiler.main(args.toArray(String[]::new));
    }

    private Path getSourcePath(Path gameJar) {
        return outputPath.resolve("sources").resolve(gameJar.getFileName());
    }

    private static void diff(Path a, Path b, Path outputPath, Path outputDir) throws IOException {
        var operation = DiffOperation.builder()
            .aPath(a)
            .bPath(b)
            .outputPath(outputPath)
            .build();

        System.out.println(":diffing...");
        var result = operation.operate();
        result.summary.print(System.out, false);

        System.out.println(":unzipping...");
        try (var fs = FileSystemReference.openJar(outputPath, false)) {
            for (Path root : fs.getFs().getRootDirectories()) {
                try (var paths = Files.walk(root)) {
                    var iter = paths.filter(Files::isRegularFile).iterator();
                    while (iter.hasNext()) {
                        var path = iter.next();
                        var pathStr = root.relativize(path).toString();
                        var targetPath = outputDir.resolve(pathStr);
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static VersionManifest.Library libraryForMinecraft(VersionManifest manifest) {
        return new VersionManifest.Library(
            "net.minecraft:minecraft:" + manifest.id(),
            new VersionManifest.Library.Downloads(
                new VersionManifest.Library.Artifact(
                    "net/minecraft/minecraft/" + manifest.id() + "/minecraft-" + manifest.id() + ".jar",
                    manifest.downloads().get("client").url()
                )
            )
        );
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Mccmp()).execute(args);
        System.exit(exitCode);
    }
}
