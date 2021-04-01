/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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
import com.codahale.metrics.RatioGauge;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class CacheMetrics {

    private CacheMetrics() {}

    public static void register(TaggedMetricRegistry taggedMetrics, Stats stats, String cacheName) {
        InternalCacheMetrics.createMetrics(stats, InternalCacheMetrics.taggedMetricName(cacheName))
                .forEach(taggedMetrics::registerWithReplacement);
    }

    static void forEachMetric(CacheMetrics.Stats stats, BiConsumer<String, Gauge<?>> consumer) {
        consumer.accept("cache.estimated.size", stats.estimatedSize());
        consumer.accept("cache.request.count", stats.requestCount());
        consumer.accept("cache.hit.count", stats.hitCount());
        consumer.accept("cache.hit.ratio", stats.hitRatio());
        consumer.accept("cache.miss.count", stats.missCount());
        consumer.accept("cache.miss.ratio", stats.missRatio());
        consumer.accept("cache.eviction.count", stats.evictionCount());
        consumer.accept("cache.load.success.count", stats.loadSuccessCount());
        consumer.accept("cache.load.failure.count", stats.loadFailureCount());
        consumer.accept("cache.load.average.millis", stats.loadAverageMillis());
        stats.maximumSize().ifPresent(maximumSizeGauge -> consumer.accept("cache.maximum.size", maximumSizeGauge));
        stats.weightedSize().ifPresent(weightedSizeGauge -> consumer.accept("cache.weighted.size", weightedSizeGauge));
    }

    public interface Stats {

        Gauge<Long> estimatedSize();

        Optional<Gauge<Long>> weightedSize();

        Optional<Gauge<Long>> maximumSize();

        Gauge<Long> requestCount();

        Gauge<Long> hitCount();

        Gauge<Long> missCount();

        Gauge<Long> evictionCount();

        Gauge<Long> loadSuccessCount();

        Gauge<Long> loadFailureCount();

        Gauge<Double> loadAverageMillis();

        default Gauge<Double> hitRatio() {
            return new RatioGauge() {
                @Override
                protected Ratio getRatio() {
                    return Ratio.of(
                            hitCount().getValue().doubleValue(),
                            requestCount().getValue().doubleValue());
                }
            };
        }

        default Gauge<Double> missRatio() {
            return new RatioGauge() {
                @Override
                protected Ratio getRatio() {
                    return Ratio.of(
                            missCount().getValue().doubleValue(),
                            requestCount().getValue().doubleValue());
                }
            };
        }
    }
}
