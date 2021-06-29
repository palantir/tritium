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

package com.palantir.tritium.metrics;

import static com.palantir.logsafe.Preconditions.checkArgument;
import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@SuppressWarnings("BanGuavaCaches") // this implementation is explicitly for Guava caches
final class CacheMetricSet implements MetricSet {

    private final Cache<?, ?> cache;
    private final String cacheName;

    private CacheMetricSet(Cache<?, ?> cache, String cacheName) {
        this.cache = checkNotNull(cache, "cache");
        this.cacheName = checkNotNull(cacheName, "cacheName");
        checkArgument(!cacheName.trim().isEmpty(), "Cache name cannot be blank or empty");
    }

    static CacheMetricSet create(Cache<?, ?> cache, String cacheName) {
        return new CacheMetricSet(cache, cacheName);
    }

    @Override
    @SuppressWarnings("MutableMethodReturnType") // API
    public Map<String, Metric> getMetrics() {
        return ImmutableMap.copyOf(InternalCacheMetrics.createMetrics(
                GuavaStats.create(cache, 1, TimeUnit.SECONDS), metricName -> cacheName + '.' + metricName));
    }

    static class GuavaStats implements CacheMetrics.Stats {
        private final Cache<?, ?> cache;
        private final Supplier<CacheStats> stats;

        @VisibleForTesting
        GuavaStats(Cache<?, ?> cache, Supplier<CacheStats> stats) {
            this.cache = cache;
            this.stats = stats;
        }

        static CacheMetrics.Stats create(Cache<?, ?> cache, long duration, TimeUnit timeUnit) {
            Supplier<CacheStats> statsSupplier = Suppliers.memoizeWithExpiration(cache::stats, duration, timeUnit);
            return new GuavaStats(cache, statsSupplier);
        }

        private CacheStats stats() {
            return stats.get();
        }

        @Override
        public Gauge<Long> estimatedSize() {
            return cache::size;
        }

        @Override
        public Optional<Gauge<Long>> weightedSize() {
            // Guava does not expose this after construction
            return Optional.empty();
        }

        @Override
        public Optional<Gauge<Long>> maximumSize() {
            // Guava does not expose this after construction
            return Optional.empty();
        }

        @Override
        public Gauge<Long> requestCount() {
            return () -> stats().requestCount();
        }

        @Override
        public Gauge<Long> hitCount() {
            return () -> stats().hitCount();
        }

        @Override
        public Gauge<Long> missCount() {
            return () -> stats().missCount();
        }

        @Override
        public Gauge<Long> evictionCount() {
            return () -> stats().evictionCount();
        }

        @Override
        public Gauge<Long> loadSuccessCount() {
            return () -> stats().loadSuccessCount();
        }

        @Override
        public Gauge<Long> loadFailureCount() {
            return () -> stats().loadExceptionCount();
        }

        @Override
        public Gauge<Double> loadAverageMillis() {
            // convert nanoseconds to milliseconds
            return () -> stats().averageLoadPenalty() / 1_000_000.0d;
        }
    }
}
