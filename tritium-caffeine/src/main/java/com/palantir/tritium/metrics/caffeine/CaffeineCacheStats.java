/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.tritium.metrics.InternalCacheMetrics;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CaffeineCacheStats {

    private static final Logger log = LoggerFactory.getLogger(CaffeineCacheStats.class);
    private static final String STATS_DISABLED = "cache.stats.disabled";

    private CaffeineCacheStats() {}

    /**
     * Register specified cache with the given metric registry.
     *
     * Callers should ensure that they have {@link Caffeine#recordStats() enabled stats recording}
     * {@code Caffeine.newBuilder().recordStats()} otherwise there are no cache metrics to register.
     *
     * @deprecated use {@link #registerCache(TaggedMetricRegistry, Cache, String)}
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     */
    @Deprecated
    public static void registerCache(MetricRegistry registry, Cache<?, ?> cache, String name) {
        checkNotNull(registry, "registry");
        checkNotNull(cache, "cache");
        checkNotNull(name, "name");
        if (cache.policy().isRecordingStats()) {
            CaffeineCacheMetrics.create(cache, name)
                    .getMetrics()
                    .forEach((key, value) -> MetricRegistries.registerWithReplacement(registry, key, value));
        } else {
            warnNotRecordingStats(name, registry.counter(MetricRegistry.name(name, STATS_DISABLED)));
        }
    }

    /**
     * Register specified cache with the given metric registry.
     *
     * Callers should ensure that they have {@link Caffeine#recordStats() enabled stats recording}
     * {@code Caffeine.newBuilder().recordStats()} otherwise there are no cache metrics to register.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     */
    public static void registerCache(TaggedMetricRegistry registry, Cache<?, ?> cache, @Safe String name) {
        checkNotNull(registry, "registry");
        checkNotNull(cache, "cache");
        checkNotNull(name, "name");
        if (cache.policy().isRecordingStats()) {
            CaffeineCacheTaggedMetrics.create(cache, name).getMetrics().forEach(registry::registerWithReplacement);
        } else {
            warnNotRecordingStats(
                    name,
                    registry.counter(InternalCacheMetrics.taggedMetricName(name).apply(STATS_DISABLED)));
        }
    }

    /**
     * Creates a {@link StatsCounter} with tagged metrics for tracking caches hits, misses, evictions, and load times.
     * <p>Example usage:</p>
     * {@code
     * Caffeine.newBuilder()
     *     .recordStats(() -> CaffeineCacheStats.record(taggedMetricRegistry, "name")
     *     .build()
     * }
     * @param registry tagged metric registry
     * @param name safe cache name
     * @return stats counter
     */
    public static StatsCounter record(TaggedMetricRegistry registry, @Safe String name) {
        Function<String, MetricName> nameFunction = InternalCacheMetrics.taggedMetricName(name);
        return new TaggedCaffeineStatsCounter(
                registry.counter(nameFunction.apply("cache.hit.count")),
                registry.counter(nameFunction.apply("cache.miss.count")),
                registry.timer(nameFunction.apply("cache.load.success")),
                registry.timer(nameFunction.apply("cache.load.failure")),
                registry.counter(cause(nameFunction.apply("cache.eviction.count"), "total")),
                registry.counter(cause(nameFunction.apply("cache.eviction.count"), "explicit")),
                registry.counter(cause(nameFunction.apply("cache.eviction.count"), "replaced")),
                registry.counter(cause(nameFunction.apply("cache.eviction.count"), "collected")),
                registry.counter(cause(nameFunction.apply("cache.eviction.count"), "expired")),
                registry.counter(cause(nameFunction.apply("cache.eviction.count"), "size")));
    }

    private static void warnNotRecordingStats(@Safe String name, Counter counter) {
        counter.inc();
        log.warn(
                "Registered cache does not have stats recording enabled, stats will always be zero. "
                        + "To enable cache metrics, stats recording must be enabled when constructing the cache: "
                        + "Caffeine.newBuilder().recordStats()",
                SafeArg.of("cacheName", name));
    }

    static <K> ImmutableMap<K, Gauge<?>> createCacheGauges(Cache<?, ?> cache, Function<String, K> metricNamer) {
        return InternalCacheMetrics.createMetrics(CaffeineStats.create(cache, 1, TimeUnit.SECONDS), metricNamer);
    }

    private static MetricName cause(MetricName metricName, String cause) {
        return MetricName.builder().from(metricName).putSafeTags("cause", cause).build();
    }

    @VisibleForTesting
    static final class TaggedCaffeineStatsCounter implements StatsCounter {
        private final Counter hitCounter;
        private final Counter missCounter;
        private final Timer successTimer;
        private final Timer failureTimer;
        private final Counter evictionCounter;
        private final Counter explicitEviction;
        private final Counter replacedEviction;
        private final Counter collectedEviction;
        private final Counter expiredEviction;
        private final Counter sizeEviction;

        TaggedCaffeineStatsCounter(
                Counter hitCounter,
                Counter missCounter,
                Timer successTimer,
                Timer failureTimer,
                Counter evictionCounter,
                Counter explicitEviction,
                Counter replacedEviction,
                Counter collectedEviction,
                Counter expiredEviction,
                Counter sizeEviction) {
            this.hitCounter = hitCounter;
            this.missCounter = missCounter;
            this.successTimer = successTimer;
            this.failureTimer = failureTimer;
            this.evictionCounter = evictionCounter;
            this.explicitEviction = explicitEviction;
            this.replacedEviction = replacedEviction;
            this.collectedEviction = collectedEviction;
            this.expiredEviction = expiredEviction;
            this.sizeEviction = sizeEviction;
        }

        @Override
        public void recordHits(int count) {
            hitCounter.inc(count);
        }

        @Override
        public void recordMisses(int count) {
            missCounter.inc(count);
        }

        @Override
        public void recordLoadSuccess(long loadTime) {
            successTimer.update(loadTime, TimeUnit.NANOSECONDS);
        }

        @Override
        public void recordLoadFailure(long loadTime) {
            failureTimer.update(loadTime, TimeUnit.NANOSECONDS);
        }

        @Override
        @SuppressWarnings("deprecation") // backward compatibility
        public void recordEviction() {
            evictionCounter.inc();
        }

        @Override
        public void recordEviction(int weight, RemovalCause cause) {
            evictionCounter.inc();
            switch (cause) {
                case EXPLICIT:
                    explicitEviction.inc(weight);
                    break;
                case REPLACED:
                    replacedEviction.inc(weight);
                    break;
                case COLLECTED:
                    collectedEviction.inc(weight);
                    break;
                case EXPIRED:
                    expiredEviction.inc(weight);
                    break;
                case SIZE:
                    sizeEviction.inc(weight);
                    break;
            }
        }

        @Override
        public CacheStats snapshot() {
            Snapshot successTimerSnapshot = successTimer.getSnapshot();
            Snapshot failureTimerSnapshot = failureTimer.getSnapshot();
            return CacheStats.of(
                    hitCounter.getCount(),
                    missCounter.getCount(),
                    successTimer.getCount(),
                    failureTimer.getCount(),
                    Math.round((successTimerSnapshot.getMean() * successTimerSnapshot.size())
                            + (failureTimerSnapshot.getMean() * failureTimerSnapshot.size())),
                    evictionCounter.getCount(),
                    explicitEviction.getCount()
                            + replacedEviction.getCount()
                            + collectedEviction.getCount()
                            + expiredEviction.getCount()
                            + sizeEviction.getCount());
        }
    }
}
