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

package com.palantir.tritium.metrics;

import static com.palantir.logsafe.Preconditions.checkArgument;
import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.Gauge;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.Safe;
import com.palantir.tritium.metrics.registry.MetricName;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("BanGuavaCaches") // this implementation is explicitly for Guava caches
final class CacheTaggedMetrics {

    private final Cache<?, ?> cache;
    private final String cacheName;

    private CacheTaggedMetrics(Cache<?, ?> cache, @Safe String cacheName) {
        String name = checkNotNull(cacheName, "cacheName").trim();
        checkArgument(!name.isEmpty(), "Cache name cannot be blank or empty");
        this.cache = checkNotNull(cache, "cache");
        this.cacheName = name;
    }

    static CacheTaggedMetrics create(Cache<?, ?> cache, @Safe String cacheName) {
        return new CacheTaggedMetrics(cache, cacheName);
    }

    ImmutableMap<MetricName, Gauge<?>> getMetrics() {
        return InternalCacheMetrics.createMetrics(
                CacheMetricSet.GuavaStats.create(this.cache, 1, TimeUnit.SECONDS),
                InternalCacheMetrics.taggedMetricName(cacheName));
    }
}
