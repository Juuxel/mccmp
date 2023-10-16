/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.mccmp;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class Futures {
    public static <T> CompletableFuture<T> runFirstSuccessful(
        Predicate<? super Throwable> continueOnErrorPredicate,
        List<Supplier<CompletableFuture<T>>> futures
    ) {
        var iter = futures.iterator();
        CompletableFuture<T> future = iter.next().get();

        while (iter.hasNext()) {
            var next = iter.next();
            future = future.exceptionallyCompose(throwable -> {
                if (getCauseChain(throwable).anyMatch(continueOnErrorPredicate)) {
                    return next.get();
                }

                throw throwable instanceof RuntimeException e ? e : new RuntimeException(throwable);
            });
        }

        return future;
    }

    private static Stream<Throwable> getCauseChain(Throwable start) {
        return Stream.iterate(start, Objects::nonNull, Throwable::getCause);
    }
}
