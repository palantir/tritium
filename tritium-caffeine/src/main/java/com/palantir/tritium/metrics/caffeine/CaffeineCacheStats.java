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

import static com.google.common.base.Preconditions.checkNotNull;

import com.codahale.metrics.Clock;
import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.annotations.VisibleForTesting;
import com.palantir.tritium.metrics.MetricRegistries;

public final class CaffeineCacheStats {

    private CaffeineCacheStats() {
        throw new UnsupportedOperationException();
    }

    /**
     * Register specified cache with the given metric registry.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param name cache name
     */
    public static <C extends Cache<?, ?>> void registerCache(MetricRegistry registry, Cache<?, ?> cache, String name) {
        registerCache(registry, cache, name, Clock.defaultClock());
    }

    @VisibleForTesting
    static void registerCache(MetricRegistry registry, Cache<?, ?> cache, String name, @VisibleForTesting Clock clock) {
        checkNotNull(registry, "registry");
        checkNotNull(cache, "cache");
        checkNotNull(name, "name");
        checkNotNull(clock, "clock");
        CaffeineCacheMetricSet.create(cache, name, clock)
                .getMetrics()
                .forEach((key, value) -> MetricRegistries.registerWithReplacement(registry, key, value));
    }

}
