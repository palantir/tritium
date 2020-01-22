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
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
import com.google.common.annotations.Beta;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Not intended for direct external usage, intended solely for sharing between tritium-metrics and tritium-caffeine.
 *
 * <p>See * {@link MetricRegistries#registerCache(MetricRegistry, Cache, String)}, *
 * {@link MetricRegistries#registerCache(TaggedMetricRegistry, Cache, String)} for intended use.
 */
@Beta
public final class InternalCacheMetrics {
    private InternalCacheMetrics() {}

    public static <K> ImmutableMap<K, Gauge<?>> createMetrics(Stats stats, Function<String, K> metricNamer) {
        ImmutableMap.Builder<K, Gauge<?>> builder = ImmutableMap.builder();
        stats.forEach((name, gauge) -> builder.put(metricNamer.apply(name), gauge));
        return builder.build();
    }

    @SuppressWarnings("NoFunctionalReturnType")
    public static Function<String, MetricName> taggedMetricName(String cacheName) {
        return name -> MetricName.builder()
                .safeName(name)
                .putSafeTags("cache", cacheName)
                .build();
    }

    @SuppressWarnings("SummaryJavadoc")
    public interface Stats {

        default void forEach(BiConsumer<String, Gauge<?>> consumer) {
            consumer.accept("cache.estimated.size", estimatedSize());
            consumer.accept("cache.request.count", requestCount());
            consumer.accept("cache.hit.count", hitCount());
            consumer.accept("cache.hit.ratio", hitRatio());
            consumer.accept("cache.miss.count", missCount());
            consumer.accept("cache.miss.ratio", missRatio());
            consumer.accept("cache.eviction.count", evictionCount());
            consumer.accept("cache.load.success.count", loadSuccessCount());
            consumer.accept("cache.load.failure.count", loadFailureCount());
            consumer.accept("cache.load.average.millis", loadAverageMillis());
            maximumSize().ifPresent(maximumSizeGauge -> consumer.accept("cache.maximum.size", maximumSizeGauge));
            weightedSize().ifPresent(weightedSizeGauge -> consumer.accept("cache.weighted.size", weightedSizeGauge));
        }

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
                    return RatioGauge.Ratio.of(
                            hitCount().getValue().doubleValue(),
                            requestCount().getValue().doubleValue());
                }
            };
        }

        default Gauge<Double> missRatio() {
            return new RatioGauge() {
                @Override
                protected Ratio getRatio() {
                    return RatioGauge.Ratio.of(
                            missCount().getValue().doubleValue(),
                            requestCount().getValue().doubleValue());
                }
            };
        }
    }
}
