package juuxel.mccmp;

import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import net.fabricmc.tinyremapper.FileSystemReference;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Mappings {
    public static Path extractMappings(Path gameJar, Path mappingsJar) throws IOException {
        var mappingsJarName = mappingsJar.getFileName().toString();
        var outputFileName = mappingsJarName.substring(0, mappingsJarName.length() - ".jar".length()) + ".tiny";
        var outputPath = mappingsJar.resolveSibling(outputFileName);

        if (Files.exists(outputPath)) return outputPath;

        try (var fs = FileSystemReference.openJar(mappingsJar, false)) {
            Files.copy(fs.getPath("mappings", "mappings.tiny"), outputPath);
        }

        boolean isV1;

        try (var reader = Files.newBufferedReader(outputPath)) {
            isV1 = isTinyV1(reader);
        }

        if (isV1) {
            var tempFile = Files.createTempFile(null, "-input.tiny");
            Files.copy(outputPath, tempFile, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(outputPath);

            // Propose names using Stitch
            try {
                var args = new String[] {
                    gameJar.toAbsolutePath().toString(),
                    tempFile.toAbsolutePath().toString(),
                    outputPath.toAbsolutePath().toString(),
                };
                new CommandProposeFieldNames().run(args);
                Files.delete(tempFile);
            } catch (IOException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return outputPath;
    }

    private static boolean isTinyV1(Reader reader) throws IOException {
        return reader.read() == 'v'; // v1 mappings start with v1, v2 mappings start with tiny
    }
}
