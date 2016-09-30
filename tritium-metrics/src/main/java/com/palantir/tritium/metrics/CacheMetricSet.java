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

package com.palantir.tritium.metrics;

import static com.google.common.base.Preconditions.checkNotNull;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

class CacheMetricSet implements MetricSet {

    private final Cache<?, ?> cache;
    private final String metricsPrefix;

    CacheMetricSet(Cache<?, ?> cache, String metricsPrefix) {
        checkNotNull(cache);
        this.cache = cache;
        this.metricsPrefix = metricsPrefix;
    }

    @Override
    public Map<String, Metric> getMetrics() {
        ImmutableMap.Builder<String, Metric> cacheMetrics = ImmutableMap.builder();

        cacheMetrics.put(MetricRegistry.name(metricsPrefix, "request", "count"),
                new Gauge<Long>() {
                    @Override
                    public Long getValue() {
                        return cache.stats().requestCount();
                    }
                });

        cacheMetrics.put(MetricRegistry.name(metricsPrefix, "hit", "count"),
                new Gauge<Long>() {
                    @Override
                    public Long getValue() {
                        return cache.stats().hitCount();
                    }
                });
        cacheMetrics.put(MetricRegistry.name(metricsPrefix, "hit", "ratio"),
                new Gauge<Double>() {
                    @Override
                    public Double getValue() {
                        CacheStats stats = cache.stats();
                        return stats.hitCount() / (1.0d * stats.requestCount());
                    }
                });

        cacheMetrics.put(MetricRegistry.name(metricsPrefix, "miss", "count"),
                new Gauge<Long>() {
                    @Override
                    public Long getValue() {
                        return cache.stats().missCount();
                    }
                });
        cacheMetrics.put(MetricRegistry.name(metricsPrefix, "miss", "ratio"),
                new Gauge<Double>() {
                    @Override
                    public Double getValue() {
                        CacheStats stats = cache.stats();
                        return stats.missCount() / (1.0d * stats.requestCount());
                    }
                });

        cacheMetrics.put(MetricRegistry.name(metricsPrefix, "eviction", "count"),
                new Gauge<Long>() {
                    @Override
                    public Long getValue() {
                        return cache.stats().evictionCount();
                    }
                });

        cacheMetrics.put(MetricRegistry.name(metricsPrefix, "averageLoadPenalty"),
                new Gauge<Double>() {
                    @Override
                    public Double getValue() {
                        return cache.stats().averageLoadPenalty();
                    }
                });

        return cacheMetrics.build();
    }

}
