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

import static com.palantir.logsafe.Preconditions.checkArgument;
import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

final class CaffeineCacheMetrics implements MetricSet {

    private final Cache<?, ?> cache;
    private final String cacheName;

    private CaffeineCacheMetrics(Cache<?, ?> cache, String cacheName) {
        this.cache = checkNotNull(cache, "cache");
        this.cacheName = checkNotNull(cacheName, "cacheName");
        checkArgument(!cacheName.trim().isEmpty(), "Cache name cannot be blank or empty");
    }

    static CaffeineCacheMetrics create(Cache<?, ?> cache, String cacheName) {
        return new CaffeineCacheMetrics(cache, cacheName);
    }

    @Override
    @SuppressWarnings("MutableMethodReturnType") // API
    public Map<String, Metric> getMetrics() {
        return ImmutableMap.copyOf(
                CaffeineCacheStats.createCacheGauges(cache, name -> cacheName + '.' + name));
    }
}
