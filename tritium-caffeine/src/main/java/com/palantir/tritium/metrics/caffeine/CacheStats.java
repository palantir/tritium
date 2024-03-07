/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.metrics.caffeine;

import com.codahale.metrics.Counter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.metrics.cache.CacheMetrics;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Supplier;
import org.checkerframework.checker.index.qual.NonNegative;

public final class CacheStats implements StatsCounter {
    private final CacheMetrics metrics;
    private final String name;
    private final Counter hitCounter;
    private final Counter missCounter;

    private final LongAdder totalLoadNanos = new LongAdder();
    private final Counter loadSuccessCounter;
    private final Counter loadFailureCounter;
    private final Counter evictionsTotalCounter;
    private final ImmutableMap<RemovalCause, Counter> evictionCounters;

    /**
     * Creates a {@link CacheStats} instance that registers metrics for Caffeine cache statistics.
     * <p>
     * Example method
     * <pre>
     *         CacheStats cacheStats = CacheStats.of(taggedMetricRegistry, "your-cache-name");
     *         LoadingCache&lt;Integer, String> cache = cacheStats.register(Caffeine.newBuilder()
     *                 .recordStats(cacheStats.record())
     *                 .build(key -> computeSomethingExpensive(key));
     * </pre>
     * @param taggedMetricRegistry tagged metric registry to add cache metrics
     * @param name cache name
     * @return Caffeine stats instance to register via
     * {@link com.github.benmanes.caffeine.cache.Caffeine#recordStats(Supplier)}.
     */
    public static CacheStats of(TaggedMetricRegistry taggedMetricRegistry, String name) {
        return new CacheStats(CacheMetrics.of(taggedMetricRegistry), name);
    }

    public static CacheStats of(CacheMetrics metrics, String name) {
        return new CacheStats(metrics, name);
    }

    private CacheStats(CacheMetrics metrics, String name) {
        this.metrics = metrics;
        this.name = name;
        this.hitCounter = metrics.hitCount(name);
        this.missCounter = metrics.missCount(name);
        this.loadSuccessCounter = metrics.loadSuccessCount(name);
        this.loadFailureCounter = metrics.loadFailureCount(name);
        this.evictionsTotalCounter = metrics.evictionCount(name);
        this.evictionCounters = Arrays.stream(RemovalCause.values())
                .collect(ImmutableMap.toImmutableMap(Function.identity(), cause -> metrics.evictions()
                        .cache(name)
                        .cause(cause.toString())
                        .build()));
        metrics.requestCount().cache(name).build(() -> hitCounter.getCount() + missCounter.getCount());
    }

    public <K, V, C extends Cache<K, V>> C register(C cache) {
        metrics.estimatedSize().cache(name).build(cache::estimatedSize);
        metrics.maximumSize().cache(name).build(() -> cache.policy()
                .eviction()
                .map(Policy.Eviction::getMaximum)
                .orElse(-1L));
        metrics.weightedSize().cache(name).build(() -> cache.policy()
                .eviction()
                .flatMap(e -> e.weightedSize().stream().boxed().findFirst())
                .orElse(0L));
        metrics.hitRatio().cache(name).build(() -> {
            double hitCount = hitCounter.getCount();
            return hitCount / (hitCount + missCounter.getCount());
        });
        metrics.missRatio().cache(name).build(() -> {
            double missCount = missCounter.getCount();
            return missCount / (hitCounter.getCount() + missCount);
        });
        metrics.loadAverageMillis()
                .cache(name)
                .build(() ->
                        // convert nanoseconds to milliseconds
                        totalLoadNanos.sum() / 1_000_000.0d);
        return cache;
    }

    public <K, V> LoadingCache<K, V> register(LoadingCache<K, V> cache) {
        return (LoadingCache<K, V>) register((Cache<K, V>) cache);
    }

    public Supplier<? extends StatsCounter> recorder() {
        return () -> this;
    }

    @Override
    public void recordHits(@NonNegative int count) {
        hitCounter.inc(count);
    }

    @Override
    public void recordMisses(@NonNegative int count) {
        missCounter.inc(count);
    }

    @Override
    public void recordLoadSuccess(@NonNegative long loadTime) {
        loadSuccessCounter.inc();
        totalLoadNanos.add(loadTime);
    }

    @Override
    public void recordLoadFailure(@NonNegative long loadTime) {
        loadFailureCounter.inc();
        totalLoadNanos.add(loadTime);
    }

    @Override
    public void recordEviction(@NonNegative int weight, RemovalCause cause) {
        Counter counter = evictionCounters.get(cause);
        if (counter != null) {
            counter.inc(weight);
        }
        evictionsTotalCounter.inc(weight);
    }

    @Override
    public com.github.benmanes.caffeine.cache.stats.CacheStats snapshot() {
        return com.github.benmanes.caffeine.cache.stats.CacheStats.of(
                hitCounter.getCount(),
                missCounter.getCount(),
                loadSuccessCounter.getCount(),
                loadFailureCounter.getCount(),
                totalLoadNanos.sum(),
                evictionsTotalCounter.getCount(),
                evictionCounters.values().stream().mapToLong(Counter::getCount).sum());
    }

    @Override
    public String toString() {
        return name + ": " + snapshot();
    }
}
