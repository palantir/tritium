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

import com.codahale.metrics.CachedGauge;
import com.codahale.metrics.DerivativeGauge;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.google.common.base.Function;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuppressFBWarnings({"PT_FINAL_TYPE_PARAM", "PT_FINAL_TYPE_RETURN"})
class CacheMetricSet implements MetricSet {

    private final Cache<?, ?> cache;
    private final String metricsPrefix;

    CacheMetricSet(Cache<?, ?> cache, String metricsPrefix) {
        checkNotNull(cache);
        this.cache = cache;
        this.metricsPrefix = metricsPrefix;
    }

    private <T> Gauge<T> derivedGauge(final Function<CacheStats, T> gauge) {
        // cache the snapshot
        Gauge<CacheStats> cachedStatsSnapshotGauge = new CachedGauge<CacheStats>(500, TimeUnit.MILLISECONDS) {
            @Override
            protected CacheStats loadValue() {
                return cache.stats();
            }
        };
        return new DerivativeGauge<CacheStats, T>(cachedStatsSnapshotGauge) {
            @Override
            protected T transform(CacheStats stats) {
                return gauge.apply(stats);
            }
        };
    }

    private String cacheMetricName(String... args) {
        return MetricRegistry.name(metricsPrefix, args);
    }

    @Override
    public Map<String, Metric> getMetrics() {
        ImmutableMap.Builder<String, Metric> cacheMetrics = ImmutableMap.builder();

        cacheMetrics.put(cacheMetricName("estimated", "size"),
                new CachedGauge<Long>(500, TimeUnit.MILLISECONDS) {
                    @Override
                    protected Long loadValue() {
                        return cache.size();
                    }
                });

        cacheMetrics.put(cacheMetricName("request", "count"),
                derivedGauge(new Function<CacheStats, Long>() {
                    @Override
                    public Long apply(CacheStats stats) {
                        return stats.requestCount();
                    }
                }));

        cacheMetrics.put(cacheMetricName("hit", "count"),
                derivedGauge(new Function<CacheStats, Long>() {
                    @Override
                    public Long apply(CacheStats stats) {
                        return stats.hitCount();
                    }
                }));

        cacheMetrics.put(cacheMetricName("hit", "ratio"),
                derivedGauge(new Function<CacheStats, Double>() {
                    @Override
                    public Double apply(CacheStats stats) {
                        return stats.hitCount() / (1.0d * stats.requestCount());
                    }
                }));

        cacheMetrics.put(cacheMetricName("miss", "count"),
                derivedGauge(new Function<CacheStats, Long>() {
                    @Override
                    public Long apply(CacheStats stats) {
                        return stats.missCount();
                    }
                }));

        cacheMetrics.put(cacheMetricName("miss", "ratio"),
                derivedGauge(new Function<CacheStats, Double>() {
                    @Override
                    public Double apply(CacheStats stats) {
                        return stats.missCount() / (1.0d * stats.requestCount());
                    }
                }));

        cacheMetrics.put(cacheMetricName("eviction", "count"),
                derivedGauge(new Function<CacheStats, Long>() {
                    @Override
                    public Long apply(CacheStats stats) {
                        return stats.evictionCount();
                    }
                }));

        cacheMetrics.put(cacheMetricName("load.success", "count"),
                derivedGauge(new Function<CacheStats, Long>() {
                    @Override
                    public Long apply(CacheStats stats) {
                        return stats.loadSuccessCount();
                    }
                }));

        cacheMetrics.put(cacheMetricName("load.failure", "count"),
                derivedGauge(new Function<CacheStats, Long>() {
                    @Override
                    public Long apply(CacheStats stats) {
                        return stats.loadExceptionCount();
                    }
                }));

        cacheMetrics.put(cacheMetricName("load", "average", "millis"),
                derivedGauge(new Function<CacheStats, Double>() {
                    @Override
                    public Double apply(CacheStats stats) {
                        return stats.averageLoadPenalty() / 1000000.0d;
                    }
                }));

        return cacheMetrics.build();
    }

}
