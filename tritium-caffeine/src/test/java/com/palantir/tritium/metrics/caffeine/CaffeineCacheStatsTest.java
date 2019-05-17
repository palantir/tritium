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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.palantir.logsafe.SafeArg;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Map;
import java.util.function.Function;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Test;

public class CaffeineCacheStatsTest {

    private final MetricRegistry metricRegistry = new MetricRegistry();
    private final TaggedMetricRegistry taggedMetricRegistry = new DefaultTaggedMetricRegistry();
    private Function<Integer, String> mapping = String::valueOf;

    @After
    public void after() {
        System.out.println("Metrics");
        try (ConsoleReporter reporter = ConsoleReporter.forRegistry(metricRegistry).build()) {
            reporter.report();
        }

        System.out.println("Tagged Metrics");
        Map<MetricName, Metric> metrics = taggedMetricRegistry.getMetrics();
        metrics.forEach((metricName, metric) -> {
            System.out.printf("metric: %s = %s%n", metricName, getValue(metric));
        });
    }

    private Object getValue(Metric metric) {
        if (metric instanceof Gauge) {
            return ((Gauge) metric).getValue();
        } else if (metric instanceof Counter) {
            return ((Counter) metric).getCount();
        }
        return metric;
    }

    @Test
    public void registerCacheMetrics() {
        Cache<Integer, String> cache = Caffeine.newBuilder()
                .recordStats()
                .maximumSize(2)
                .build();
        CaffeineCacheStats.registerCache(metricRegistry, cache, "test");
        assertThat(metricRegistry.getGauges().keySet())
                .contains(
                        "test.cache.estimated.size",
                        "test.cache.eviction.count",
                        "test.cache.hit.count",
                        "test.cache.hit.ratio",
                        "test.cache.load.average.millis",
                        "test.cache.load.failure.count",
                        "test.cache.load.success.count",
                        "test.cache.maximum.size",
                        "test.cache.miss.count",
                        "test.cache.miss.ratio",
                        "test.cache.request.count",
                        "test.cache.weighted.size");

        assertThat(metricRegistry.getGauges().get("test.cache.hit.count").getValue()).isEqualTo(0L);
        assertThat(metricRegistry.getGauges().get("test.cache.miss.count").getValue()).isEqualTo(0L);

        assertThat(cache.get(0, mapping)).isEqualTo("0");
        assertThat(cache.get(1, mapping)).isEqualTo("1");
        assertThat(cache.get(2, mapping)).isEqualTo("2");
        assertThat(cache.get(1, mapping)).isEqualTo("1");

        await().atMost(Duration.TEN_SECONDS).untilAsserted(() -> {
            assertThat(metricRegistry.getGauges().get("test.cache.request.count").getValue()).isEqualTo(4L);
        });

        assertThat(metricRegistry.getGauges().get("test.cache.hit.count").getValue()).isEqualTo(1L);
        assertThat(metricRegistry.getGauges().get("test.cache.miss.count").getValue()).isEqualTo(3L);
    }

    @Test
    public void registerCacheTaggedMetrics() {
        Cache<Integer, String> cache = Caffeine.newBuilder()
                .recordStats()
                .maximumSize(2)
                .build();
        CaffeineCacheStats.registerCache(taggedMetricRegistry, cache, SafeArg.of("cache", "test"));
        assertThat(taggedMetricRegistry.getMetrics().keySet())
                .extracting(MetricName::safeName)
                .contains(
                        "cache.estimated.size",
                        "cache.maximum.size",
                        "cache.weighted.size",
                        "cache.request.count",
                        "cache.hit.count",
                        "cache.hit.ratio",
                        "cache.miss.count",
                        "cache.miss.ratio",
                        "cache.eviction.count",
                        "cache.load.success.count",
                        "cache.load.failure.count",
                        "cache.load.average.millis");

        assertThat(cache.get(0, mapping)).isEqualTo("0");
        assertThat(cache.get(1, mapping)).isEqualTo("1");
        assertThat(cache.get(2, mapping)).isEqualTo("2");
        assertThat(cache.get(1, mapping)).isEqualTo("1");

        await().atMost(Duration.TEN_SECONDS).untilAsserted(() -> {
            assertThat(getMetric(taggedMetricRegistry, Gauge.class, "cache.request.count")
                    .getValue()).isEqualTo(4L);
        });

        assertThat(getMetric(taggedMetricRegistry, Gauge.class, "cache.hit.count").getValue()).isEqualTo(1L);
        assertThat(getMetric(taggedMetricRegistry, Gauge.class, "cache.miss.count").getValue()).isEqualTo(3L);
        assertThat(getMetric(taggedMetricRegistry, Gauge.class, "cache.hit.ratio").getValue()).isEqualTo(0.25);
    }

    private static <T extends Metric> T getMetric(TaggedMetricRegistry metrics, Class<T> clazz, String name) {
        return clazz.cast(metrics.getMetrics()
                .entrySet()
                .stream()
                .filter(e -> name.equals(e.getKey().safeName()))
                .filter(e -> clazz.isInstance(e.getValue()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such metric " + name))
                .getValue());
    }
}
