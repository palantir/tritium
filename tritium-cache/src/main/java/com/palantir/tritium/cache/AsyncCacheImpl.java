/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.tritium.cache;

import static com.palantir.logsafe.Preconditions.checkState;

import com.google.common.collect.Iterators;
import com.google.errorprone.annotations.MustBeClosed;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tracing.Tracers;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

class AsyncCacheImpl<K, V> implements AsyncCache<K, V> {

    private final String loadOperation;
    private final com.github.benmanes.caffeine.cache.AsyncCache<K, V> cache;

    AsyncCacheImpl(String name, com.github.benmanes.caffeine.cache.AsyncCache<K, V> cache) {
        this.loadOperation = name + " cache load";
        this.cache = cache;
    }

    @Override
    @Nullable
    public final V getIfPresent(K key) {
        return cache.synchronous().getIfPresent(key);
    }

    @Override
    @Nullable
    public final V get(K key, Function<? super K, ? extends V> mappingFunction) {
        CompletableFuture<V> future;
        try (Loader<K, V> loader = loader(mappingFunction)) {
            future = cache.get(key, loader);
        }

        return await(future);
    }

    @Override
    public final Map<K, V> getAllPresent(Iterable<? extends K> keys) {
        return cache.synchronous().getAllPresent(keys);
    }

    @Override
    public final Map<K, V> getAll(
            Iterable<? extends K> keys,
            Function<? super Set<? extends K>, ? extends Map<? extends K, ? extends V>> mappingFunction) {
        CompletableFuture<Map<K, V>> future;
        try (Loader<Set<? extends K>, Map<? extends K, ? extends V>> loader = loader(mappingFunction)) {
            future = cache.getAll(keys, loader);
        }

        return await(future);
    }

    @Override
    public final void put(K key, V value) {
        cache.synchronous().put(key, value);
    }

    @Override
    public final void putAll(Map<? extends K, ? extends V> map) {
        cache.synchronous().putAll(map);
    }

    @Override
    public final void invalidate(K key) {
        cache.synchronous().invalidate(key);
    }

    @Override
    public final void invalidateAll(Iterable<? extends K> keys) {
        cache.synchronous().invalidateAll(keys);
    }

    @Override
    public final void invalidateAll() {
        cache.synchronous().invalidateAll();
    }

    @Override
    public Iterator<Entry<K, V>> entries() {
        return Iterators.unmodifiableIterator(
                cache.synchronous().asMap().entrySet().iterator());
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                runtimeException.addSuppressed(new SafeRuntimeException("Cache load failed"));
                throw runtimeException;
            } else if (e.getCause() instanceof Error error) {
                error.addSuppressed(new SafeRuntimeException("Cache load failed"));
                throw error;
            } else {
                throw new SafeRuntimeException("Cache load failed", e.getCause());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SafeRuntimeException("Cache load interrupted", e);
        }
    }

    @MustBeClosed
    private <I, O> Loader<I, O> loader(Function<? super I, ? extends O> mappingFunction) {
        return new Loader<>(loadOperation, mappingFunction);
    }

    // This class exists to ensure that we do not start loading values until the entry is inserted into the cache.
    // This ensures that invalidateAll will remove entries that are being loaded.
    //
    // By default, Caffeine creates async cache entries with something like CompletableFuture.supplyAsync. If the load
    // completes and invalidateAll is called before the entry is inserted into the cache, then we may insert a stale
    // entry into the cache.
    private static final class Loader<I, O> implements BiFunction<I, Executor, CompletableFuture<O>>, AutoCloseable {

        private final String loadOperation;
        private final Function<? super I, ? extends O> mappingFunction;

        @Nullable
        private Runnable runnable;

        @MustBeClosed
        Loader(String loadOperation, Function<? super I, ? extends O> mappingFunction) {
            this.loadOperation = loadOperation;
            this.mappingFunction = mappingFunction;
        }

        @Override
        public CompletableFuture<O> apply(I key, Executor executor) {
            checkState(runnable == null);

            CompletableFuture<O> future = new CompletableFuture<>();

            runnable = () -> {
                try {
                    executor.execute(Tracers.wrap(loadOperation, () -> {
                        try {
                            future.complete(mappingFunction.apply(key));
                        } catch (Throwable t) {
                            future.completeExceptionally(t);
                        }
                    }));
                } catch (Throwable t) {
                    future.obtrudeException(t);
                }
            };

            return future;
        }

        @Override
        public void close() {
            if (runnable != null) {
                runnable.run();
            }
        }
    }
}
