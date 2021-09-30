/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Gauge;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.palantir.tritium.metrics.InternalCacheMetrics;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

final class CaffeineStats implements InternalCacheMetrics.Stats {

    private final Cache<?, ?> cache;
    private final Supplier<CacheStats> stats;

    @VisibleForTesting
    CaffeineStats(Cache<?, ?> cache, Supplier<CacheStats> stats) {
        this.cache = cache;
        this.stats = stats;
    }

    static InternalCacheMetrics.Stats create(Cache<?, ?> cache, long duration, TimeUnit timeUnit) {
        Supplier<CacheStats> statsSupplier = Suppliers.memoizeWithExpiration(cache::stats, duration, timeUnit);
        return new CaffeineStats(cache, statsSupplier);
    }

    private CacheStats stats() {
        return stats.get();
    }

    @Override
    public Gauge<Long> estimatedSize() {
        return cache::estimatedSize;
    }

    @Override
    public Optional<Gauge<Long>> weightedSize() {
        return Optional.of(() -> cache.policy()
                .eviction()
                .flatMap(e -> e.weightedSize().stream().boxed().findFirst())
                .orElse(0L));
    }

    @Override
    public Optional<Gauge<Long>> maximumSize() {
        return Optional.of(
                () -> cache.policy().eviction().map(Policy.Eviction::getMaximum).orElse(-1L));
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
        return () -> stats().loadFailureCount();
    }

    @Override
    public Gauge<Double> loadAverageMillis() {
        // convert nanoseconds to milliseconds
        return () -> stats().averageLoadPenalty() / 1_000_000.0d;
    }
}
