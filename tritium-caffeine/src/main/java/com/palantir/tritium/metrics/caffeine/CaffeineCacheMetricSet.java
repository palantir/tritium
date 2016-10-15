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

import com.codahale.metrics.CachedGauge;
import com.codahale.metrics.DerivativeGauge;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

final class CaffeineCacheMetricSet implements MetricSet {

    private final Cache<?, ?> cache;
    private final String metricsPrefix;

    CaffeineCacheMetricSet(Cache<?, ?> cache, String metricsPrefix) {
        checkNotNull(cache);
        this.cache = cache;
        this.metricsPrefix = metricsPrefix;
    }

    private <T> Gauge<T> derivedGauge(final Function<CacheStats, T> gauge) {
        // cache the snapshot
        Gauge<CacheStats> cacheStatsSnapshotGauge = new CachedGauge<CacheStats>(500, TimeUnit.MILLISECONDS) {
            @Override
            @SuppressFBWarnings("PT_FINAL_TYPE_RETURN") // Caffeine final type
            protected CacheStats loadValue() {
                return cache.stats();
            }
        };
        return new DerivativeGauge<CacheStats, T>(cacheStatsSnapshotGauge) {
            @Override
            @SuppressFBWarnings("PT_FINAL_TYPE_PARAM") // Caffeine final type
            protected T transform(CacheStats stats) {
                return gauge.apply(stats);
            }
        };
    }

    private String metricName(String... args) {
        return MetricRegistry.name(metricsPrefix, args);
    }

    @Override
    public Map<String, Metric> getMetrics() {
        ImmutableMap.Builder<String, Metric> cacheMetrics = ImmutableMap.builder();

        cacheMetrics.put(metricName("estimated", "size"),
                new CachedGauge<Long>(500, TimeUnit.MILLISECONDS) {
                    @Override
                    protected Long loadValue() {
                        return cache.estimatedSize();
                    }
                });

        cacheMetrics.put(metricName("request", "count"),
                derivedGauge(CacheStats::requestCount));

        cacheMetrics.put(metricName("hit", "count"),
                derivedGauge(CacheStats::hitCount));

        cacheMetrics.put(metricName("hit", "ratio"),
                derivedGauge(stats -> stats.hitCount() / (1.0d * stats.requestCount())));

        cacheMetrics.put(metricName("miss", "count"),
                derivedGauge(CacheStats::missCount));

        cacheMetrics.put(metricName("miss", "ratio"),
                derivedGauge(stats -> stats.missCount() / (1.0d * stats.requestCount())));

        cacheMetrics.put(metricName("eviction", "count"),
                derivedGauge(CacheStats::evictionCount));

        cacheMetrics.put(metricName("load", "success", "count"),
                derivedGauge(CacheStats::loadSuccessCount));

        cacheMetrics.put(metricName("load", "failure", "count"),
                derivedGauge(CacheStats::loadFailureCount));

        cacheMetrics.put(metricName("load", "average", "millis"),
                derivedGauge((stats) -> stats.averageLoadPenalty() / 1000000.0d));

        return cacheMetrics.build();
    }

}
