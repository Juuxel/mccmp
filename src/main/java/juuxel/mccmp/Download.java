/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.mccmp;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Download {
    static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(16);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .executor(EXECUTOR)
        .build();
    private static final Moshi MOSHI = new Moshi.Builder().build();

    public static <T> CompletableFuture<HttpResponse<T>> download(String url, HttpResponse.BodyHandler<T> bodyHandler) {
        var request = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(url))
            .build();
        System.out.println(":downloading " + url);
        return CLIENT.sendAsync(request, bodyHandler);
    }

    public static <T> CompletableFuture<T> json(String url, Class<T> clazz) {
        return json(url, moshi -> moshi.adapter(clazz));
    }

    public static <T> CompletableFuture<T> json(String url, TypeToken<T> type) {
        return json(url, moshi -> moshi.adapter(type.type()));
    }

    private static <T> CompletableFuture<T> json(String url, Function<Moshi, JsonAdapter<T>> adapterGetter) {
        return download(url, checkStatus(() -> HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8)))
            .thenApply(response -> {
                try {
                    return adapterGetter.apply(MOSHI).fromJson(response.body());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    public static CompletableFuture<Path> file(Path path, String url) {
        if (Files.exists(path)) return CompletableFuture.completedFuture(path);

        return download(url, checkStatus(() -> HttpResponse.BodySubscribers.ofFile(path)))
            .thenApply(HttpResponse::body);
    }

    private static <T> HttpResponse.BodyHandler<T> checkStatus(Supplier<HttpResponse.BodySubscriber<T>> bodyHandler) {
        return responseInfo -> {
            if (responseInfo.statusCode() == 200) {
                return bodyHandler.get();
            }

            throw new StatusCodeException(responseInfo.statusCode());
        };
    }

    public static final class StatusCodeException extends RuntimeException {
        private final int statusCode;

        public StatusCodeException(int statusCode) {
            super("Status code: " + statusCode);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
