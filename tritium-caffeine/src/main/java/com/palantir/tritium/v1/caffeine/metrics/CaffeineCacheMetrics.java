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

package com.palantir.tritium.v1.caffeine.metrics;

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.tritium.metrics.InternalCacheMetrics;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrument {@link Caffeine} cache with metrics.
 */
public final class CaffeineCacheMetrics {

    private static final Logger log = LoggerFactory.getLogger(CaffeineCacheMetrics.class);
    private static final String STATS_DISABLED = "cache.stats.disabled";

    private CaffeineCacheMetrics() {}

    /**
     * Register specified cache with the given metric registry.
     *
     * Callers should ensure that they have {@link Caffeine#recordStats() enabled stats recording}
     * {@code Caffeine.newBuilder().recordStats()} otherwise there are no cache metrics to register.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     * @deprecated use {@link #registerCache(TaggedMetricRegistry, Cache, String)}
     */
    @Deprecated
    public static void registerCache(MetricRegistry registry, Cache<?, ?> cache, String name) {
        checkNotNull(registry, "registry");
        checkNotNull(cache, "cache");
        checkNotNull(name, "name");
        if (cache.policy().isRecordingStats()) {
            CaffeineCacheMetricSet.create(cache, name)
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
}
