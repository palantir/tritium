/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.palantir.tritium.metrics.MetricRegistries;
import java.util.Map;

public final class CaffeineCacheStats {

    private CaffeineCacheStats() {
        throw new UnsupportedOperationException();
    }

    /**
     * Register specified cache with the given metric registry.
     *
     * @param registry metric registry
     * @param cache cache to instrument
     * @param metricsPrefix metrics prefix
     */
    public static <C extends Cache<?, ?>> void registerCache(MetricRegistry registry,
            Cache<?, ?> cache,
            String metricsPrefix) {
        checkNotNull(registry, "registry");
        checkNotNull(metricsPrefix, "metricsPrefix");
        checkNotNull(cache, "cache");

        CaffeineCacheMetricSet cacheMetrics = new CaffeineCacheMetricSet(cache, metricsPrefix);
        for (Map.Entry<String, Metric> entry : cacheMetrics.getMetrics().entrySet()) {
            MetricRegistries.registerSafe(registry, entry.getKey(), entry.getValue());
        }
    }

}
