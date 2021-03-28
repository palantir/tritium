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

import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.palantir.logsafe.Safe;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.v1.caffeine.metrics.CaffeineCacheMetrics;

/**
 * Instrument {@link Caffeine} cache with metrics.
 * @deprecated use {@link CaffeineCacheMetrics}
 */
@Deprecated // remove post 1.0
public final class CaffeineCacheStats {

    private CaffeineCacheStats() {}

    /**
     * Register specified cache with the given metric registry.
     *
     * Callers should ensure that they have {@link Caffeine#recordStats() enabled stats recording}
     * {@code Caffeine.newBuilder().recordStats()} otherwise there are no cache metrics to register.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     * @deprecated use {@link CaffeineCacheMetrics#registerCache(TaggedMetricRegistry, Cache, String)}
     */
    @Deprecated
    public static void registerCache(MetricRegistry registry, Cache<?, ?> cache, String name) {
        CaffeineCacheMetrics.registerCache(registry, cache, name);
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
     * @deprecated use {@link CaffeineCacheMetrics#registerCache(TaggedMetricRegistry, Cache, String)}
     */
    @Deprecated
    public static void registerCache(TaggedMetricRegistry registry, Cache<?, ?> cache, @Safe String name) {
        CaffeineCacheMetrics.registerCache(registry, cache, name);
    }
}
