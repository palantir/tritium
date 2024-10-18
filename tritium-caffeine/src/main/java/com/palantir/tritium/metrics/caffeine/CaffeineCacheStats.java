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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.InternalCacheMetrics;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CaffeineCacheStats {

    private static final SafeLogger log = SafeLoggerFactory.get(CaffeineCacheStats.class);

    private CaffeineCacheStats() {}

    /**
     * Register specified cache with the given metric registry.
     * <p>
     * Callers should ensure that they have {@link Caffeine#recordStats() enabled stats recording}
     * {@code Caffeine.newBuilder().recordStats()} otherwise there are no cache metrics to register.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     *
     * @deprecated use {@link #registerCache(TaggedMetricRegistry, Cache, String)}
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
            warnNotRecordingStats(
                    name,
                    registry.meter(MetricRegistry.name(
                            name, CacheMetrics.statsDisabledMetricName(name).safeName())));
        }
    }

    /**
     * Register specified cache with the given metric registry.
     * <p>
     * Callers should ensure that they have {@link Caffeine#recordStats() enabled stats recording}
     * {@code Caffeine.newBuilder().recordStats()} otherwise there are no cache metrics to register.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     * <p>
     * Soon to be deprecated, prefer {@link Caffeine#recordStats(Supplier)} and {@link CacheStats#of(TaggedMetricRegistry, String)}
     */
    // Soon to be @Deprecated
    public static void registerCache(TaggedMetricRegistry registry, Cache<?, ?> cache, @Safe String name) {
        checkNotNull(registry, "registry");
        checkNotNull(cache, "cache");
        checkNotNull(name, "name");
        if (cache.policy().isRecordingStats()) {
            CaffeineCacheTaggedMetrics.create(cache, name).getMetrics().forEach(registry::registerWithReplacement);
        } else {
            warnNotRecordingStats(name, CacheMetrics.of(registry).statsDisabled(name));
        }
    }

    private static void warnNotRecordingStats(@Safe String name, Meter counter) {
        counter.mark();
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
