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
import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.palantir.logsafe.Safe;
import com.palantir.tritium.metrics.InternalCacheMetrics;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@SuppressWarnings("WeakerAccess") // public API
public final class CaffeineCacheStats {

    private CaffeineCacheStats() {}

    /**
     * Register specified cache with the given metric registry.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     */
    public static void registerCache(MetricRegistry registry, Cache<?, ?> cache, String name) {
        checkNotNull(registry, "registry");
        checkNotNull(cache, "cache");
        checkNotNull(name, "name");

        CaffeineCacheMetrics.create(cache, name)
                .getMetrics()
                .forEach((key, value) -> MetricRegistries.registerWithReplacement(registry, key, value));
    }

    /**
     * Register specified cache with the given metric registry.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     */
    public static void registerCache(TaggedMetricRegistry registry, Cache<?, ?> cache, @Safe String name) {
        checkNotNull(registry, "registry");
        checkNotNull(cache, "cache");
        checkNotNull(name, "name");

        CaffeineCacheTaggedMetrics.create(cache, name)
                .getMetrics()
                .forEach((metricName, gauge) -> {
                    registry.remove(metricName);
                    registry.gauge(metricName, gauge);
                });
    }

    static <K> Map<K, Gauge<?>> createCacheGauges(Cache<?, ?> cache, Function<String, K> metricNamer) {
        return InternalCacheMetrics.createMetrics(CaffeineStats.create(cache, 1, TimeUnit.SECONDS), metricNamer);
    }
}
