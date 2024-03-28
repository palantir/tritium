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

import static com.palantir.logsafe.Preconditions.checkState;

import com.codahale.metrics.Counting;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.palantir.logsafe.Safe;
import com.palantir.tritium.metrics.caffeine.CacheMetrics.Load_Result;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Supplier;
import org.checkerframework.checker.index.qual.NonNegative;

public final class CacheStats implements StatsCounter, Supplier<StatsCounter> {

    private final CacheMetrics metrics;
    private final String name;
    private final Meter hitMeter;
    private final Meter missMeter;
    private final Timer loadSuccessTimer;
    private final Timer loadFailureTimer;
    private final ImmutableMap<RemovalCause, Meter> evictionMeters;
    private final ImmutableMap<RemovalCause, Meter> evictionWeightMeters;
    private final LongAdder totalLoadTime = new LongAdder();

    /**
     * Creates a {@link CacheStats} instance that can be used to record metrics for Caffeine cache statistics.
     * <p>
     * Example usage:
     * <pre>
     *     Cache&lt;Integer, String&gt; cache = CacheStats.of(taggedMetricRegistry, "your-cache-name")
     *             .register(stats -> Caffeine.newBuilder()
     *                     .recordStats(stats)
     *                     .build());
     * </pre>
     * @param taggedMetricRegistry tagged metric registry to add cache metrics
     * @param name cache name
     * @return Caffeine stats instance to register via
     * {@link com.github.benmanes.caffeine.cache.Caffeine#recordStats(Supplier)}.
     */
    public static CacheStats of(TaggedMetricRegistry taggedMetricRegistry, @Safe String name) {
        return new CacheStats(CacheMetrics.of(taggedMetricRegistry), name);
    }

    /**
     * Constructs and registers metrics for Caffeine cache statistics.
     * <p>
     * In order to record metrics, the {@code cacheFactory} must use the provided {@link CacheStats} with
     * {@link Caffeine#recordStats(Supplier)})}.
     * <p>
     * Example usage:
     * <pre>
     *     Cache&lt;Integer, String&gt; cache = CacheStats.of(taggedMetricRegistry, "your-cache-name")
     *             .register(stats -> Caffeine.newBuilder()
     *                     .recordStats(stats)
     *                     .build());
     * </pre>
     * @param cacheFactory method which will be invoked to construct the cache
     * @return the constructed cache instance
     */
    public <K, V, C extends Cache<K, V>> C register(Function<CacheStats, C> cacheFactory) {
        C cache = cacheFactory.apply(this);

        checkState(
                cache.policy().isRecordingStats(),
                "Registered cache is not recording stats. Registered caches must enabled stats recording with "
                        + ".recordStats(stats).");

        metrics.estimatedSize().cache(name).build(cache::estimatedSize);
        metrics.weightedSize().cache(name).build(() -> cache.policy()
                .eviction()
                .flatMap(e -> e.weightedSize().stream().boxed().findFirst())
                .orElse(null));
        metrics.maximumSize().cache(name).build(() -> cache.policy()
                .eviction()
                .map(Policy.Eviction::getMaximum)
                .orElse(null));

        return cache;
    }

    private CacheStats(CacheMetrics metrics, @Safe String name) {
        this.metrics = metrics;
        this.name = name;
        this.hitMeter = metrics.hit(name);
        this.missMeter = metrics.miss(name);
        this.loadSuccessTimer =
                metrics.load().cache(name).result(Load_Result.SUCCESS).build();
        this.loadFailureTimer =
                metrics.load().cache(name).result(Load_Result.FAILURE).build();
        this.evictionMeters = Arrays.stream(RemovalCause.values())
                .collect(Maps.toImmutableEnumMap(cause -> cause, cause -> metrics.eviction()
                        .cache(name)
                        .cause(cause.toString())
                        .build()));
        this.evictionWeightMeters = Arrays.stream(RemovalCause.values())
                .collect(Maps.toImmutableEnumMap(cause -> cause, cause -> metrics.evictionWeight()
                        .cache(name)
                        .cause(cause.toString())
                        .build()));
    }

    @Override
    public StatsCounter get() {
        return this;
    }

    @Override
    public void recordHits(@NonNegative int count) {
        hitMeter.mark(count);
    }

    @Override
    public void recordMisses(@NonNegative int count) {
        missMeter.mark(count);
    }

    @Override
    public void recordLoadSuccess(@NonNegative long loadTime) {
        loadSuccessTimer.update(loadTime, TimeUnit.NANOSECONDS);
        totalLoadTime.add(loadTime);
    }

    @Override
    public void recordLoadFailure(@NonNegative long loadTime) {
        loadFailureTimer.update(loadTime, TimeUnit.NANOSECONDS);
        totalLoadTime.add(loadTime);
    }

    @Override
    public void recordEviction(@NonNegative int weight, RemovalCause cause) {
        Meter evictionMeter = evictionMeters.get(cause);
        if (evictionMeter != null) {
            evictionMeter.mark();
        }
        Meter evictionWeightMeter = evictionWeightMeters.get(cause);
        if (evictionWeightMeter != null) {
            evictionWeightMeter.mark(weight);
        }
    }

    @Override
    public com.github.benmanes.caffeine.cache.stats.CacheStats snapshot() {
        return com.github.benmanes.caffeine.cache.stats.CacheStats.of(
                hitMeter.getCount(),
                missMeter.getCount(),
                loadSuccessTimer.getCount(),
                loadFailureTimer.getCount(),
                totalLoadTime.sum(),
                evictionMeters.values().stream().mapToLong(Counting::getCount).sum(),
                evictionWeightMeters.values().stream()
                        .mapToLong(Counting::getCount)
                        .sum());
    }

    @Override
    public String toString() {
        return name + ": " + snapshot();
    }
}
