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

import com.codahale.metrics.Gauge;
import com.google.common.collect.ImmutableSortedMap;
import com.palantir.tritium.metrics.registry.MetricName;
import java.util.Map;
import java.util.function.Function;

public final class CacheMetrics {
    private CacheMetrics() {}

    public static <K extends Comparable<K>> Map<K, Gauge<?>> createMetrics(
            Stats stats,
            Function<String, K> metricNamer) {
        return ImmutableSortedMap.<K, Gauge<?>>naturalOrder()
                .put(metricNamer.apply("cache.estimated.size"), (Gauge<Long>) stats::estimatedSize)
                .put(metricNamer.apply("cache.maximum.size"), (Gauge<Long>) stats::maximumSize)
                .put(metricNamer.apply("cache.weighted.size"), (Gauge<Long>) stats::weightedSize)
                .put(metricNamer.apply("cache.request.count"), (Gauge<Long>) stats::requestCount)
                .put(metricNamer.apply("cache.hit.count"), (Gauge<Long>) stats::hitCount)
                .put(metricNamer.apply("cache.hit.ratio"), (Gauge<Double>) stats::hitRatio)
                .put(metricNamer.apply("cache.miss.count"), (Gauge<Long>) stats::missCount)
                .put(metricNamer.apply("cache.miss.ratio"), (Gauge<Double>) stats::missRatio)
                .put(metricNamer.apply("cache.eviction.count"), (Gauge<Long>) stats::evictionCount)
                .put(metricNamer.apply("cache.load.success.count"), (Gauge<Long>) stats::loadSuccessCount)
                .put(metricNamer.apply("cache.load.failure.count"), (Gauge<Long>) stats::loadFailureCount)
                .put(metricNamer.apply("cache.load.average.millis"), (Gauge<Double>) stats::loadAverageMillis)
                .build();
    }

    public static Function<String, MetricName> taggedMetricName(String cacheName) {
        return name -> MetricName.builder()
                .safeName(name)
                .putSafeTags("cache", cacheName)
                .build();
    }

    public interface Stats {
        long estimatedSize();
        long weightedSize();
        long maximumSize();
        long requestCount();
        long hitCount();
        long missCount();
        long evictionCount();
        long loadSuccessCount();
        long loadFailureCount();
        double loadAverageMillis();

        default double hitRatio() {
            long requestCount = requestCount();
            if (requestCount == 0) {
                return 0;
            } else {
                return hitCount() / ((double) requestCount);
            }
        }

        default double missRatio() {
            long requestCount = requestCount();
            if (requestCount == 0) {
                return 0;
            } else {
                return missCount() / ((double) requestCount);
            }
        }
    }
}
