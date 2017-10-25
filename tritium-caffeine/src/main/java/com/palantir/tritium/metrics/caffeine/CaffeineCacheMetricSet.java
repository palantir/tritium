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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.codahale.metrics.CachedGauge;
import com.codahale.metrics.Clock;
import com.codahale.metrics.DerivativeGauge;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nullable;

final class CaffeineCacheMetricSet implements MetricSet {

    private final Cache<?, ?> cache;
    private final String cacheName;
    private final Clock clock;
    private final Gauge<CacheStats> statsGauge;

    private CaffeineCacheMetricSet(Cache<?, ?> cache, String cacheName, Clock clock, Gauge<CacheStats> statsGauge) {
        this.cache = checkNotNull(cache, "cache");
        this.cacheName = checkNotNull(cacheName, "cacheName");
        this.clock = checkNotNull(clock, "clock");
        this.statsGauge = checkNotNull(statsGauge, "statsGauge");
        checkArgument(!cacheName.trim().isEmpty(), "Cache name cannot be blank or empty");
    }

    static CaffeineCacheMetricSet create(Cache<?, ?> cache, String cacheName, Clock clock) {
        return new CaffeineCacheMetricSet(cache, cacheName, clock,
                createCachedCacheStats(cache, clock, 5, TimeUnit.SECONDS));
    }

    private <T> Gauge<T> derivedGauge(final Function<CacheStats, T> gauge) {
        return transformingGauge(statsGauge, gauge);
    }

    static <T> Gauge<T> transformingGauge(Gauge<CacheStats> cachedStatsSnapshotGauge, Function<CacheStats, T> gauge) {
        // cache the snapshot
        checkNotNull(gauge, "gauge");
        return new DerivativeGauge<CacheStats, T>(cachedStatsSnapshotGauge) {
            @Nullable
            @Override
            protected T transform(CacheStats stats) {
                return (stats == null) ? null : gauge.apply(stats);
            }
        };
    }

    static Gauge<CacheStats> createCachedCacheStats(Cache<?, ?> cache, Clock clock, long timeout, TimeUnit unit) {
        return new CachedGauge<CacheStats>(clock, timeout, unit) {
            @Override
            protected CacheStats loadValue() {
                return cache.stats();
            }
        };
    }

    private String cacheMetricName(String... args) {
        return MetricRegistry.name(MetricRegistry.name(cacheName, "cache"), args);
    }

    @Override
    public Map<String, Metric> getMetrics() {
        ImmutableMap.Builder<String, Metric> cacheMetrics = ImmutableMap.builder();

        cacheMetrics.put(cacheMetricName("estimated", "size"),
                new CachedGauge<Long>(clock, 500, TimeUnit.MILLISECONDS) {
                    @Override
                    protected Long loadValue() {
                        return cache.estimatedSize();
                    }
                });

        cacheMetrics.put(cacheMetricName("request", "count"),
                derivedGauge(CacheStats::requestCount));

        cacheMetrics.put(cacheMetricName("hit", "count"),
                derivedGauge(CacheStats::hitCount));

        cacheMetrics.put(cacheMetricName("hit", "ratio"),
                derivedGauge(stats -> stats.hitCount() / (1.0d * stats.requestCount())));

        cacheMetrics.put(cacheMetricName("miss", "count"),
                derivedGauge(CacheStats::missCount));

        cacheMetrics.put(cacheMetricName("miss", "ratio"),
                derivedGauge(stats -> stats.missCount() / (1.0d * stats.requestCount())));

        cacheMetrics.put(cacheMetricName("eviction", "count"),
                derivedGauge(CacheStats::evictionCount));

        cacheMetrics.put(cacheMetricName("load", "success", "count"),
                derivedGauge(CacheStats::loadSuccessCount));

        cacheMetrics.put(cacheMetricName("load", "failure", "count"),
                derivedGauge(CacheStats::loadFailureCount));

        cacheMetrics.put(cacheMetricName("load", "average", "millis"),
                derivedGauge(stats -> stats.averageLoadPenalty() / 1000000.0d));

        return cacheMetrics.build();
    }

}
