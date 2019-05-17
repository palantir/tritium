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
import com.palantir.logsafe.SafeArg;
import com.palantir.tritium.metrics.registry.MetricName;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("BanGuavaCaches") // this implementation is explicitly for Guava caches
final class CacheTaggedMetrics {

    private final Cache<?, ?> cache;
    private final String cacheName;

    private CacheTaggedMetrics(Cache<?, ?> cache, SafeArg<String> arg) {
        String name = checkNotNull(arg.getValue(), "cacheName").trim();
        checkArgument(!name.isEmpty(), "Cache name cannot be blank or empty");
        this.cache = checkNotNull(cache, "cache");
        this.cacheName = name;
    }

    static CacheTaggedMetrics create(Cache<?, ?> cache, SafeArg<String> cacheName) {
        return new CacheTaggedMetrics(cache, cacheName);
    }

    Map<MetricName, Gauge<?>> getMetrics() {
        return CacheMetrics.createMetrics(
                CacheMetricSet.GuavaStats.create(this.cache, 1, TimeUnit.SECONDS),
                CacheMetrics.taggedMetricName(cacheName));
    }
}
