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
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.awaitility.Awaitility.await;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// explicitly testing deprecated non-tagged metric registry. SortedMap is part of Metrics API
@SuppressWarnings({"deprecation", "JdkObsolete"})
final class CaffeineCacheStatsTest {

    private final MetricRegistry metricRegistry = new MetricRegistry();
    private final TaggedMetricRegistry taggedMetricRegistry = new DefaultTaggedMetricRegistry();
    private final Function<Integer, String> mapping = String::valueOf;

    @AfterEach
    @SuppressWarnings("SystemOut") // dumping metrics to standard out
    void after() {
        System.out.println("Metrics");
        try (ConsoleReporter reporter =
                ConsoleReporter.forRegistry(metricRegistry).build()) {
            reporter.report();
        }

        System.out.println("Tagged Metrics");
        Map<MetricName, Metric> metrics = taggedMetricRegistry.getMetrics();
        metrics.forEach((metricName, metric) -> System.out.printf("metric: %s = %s%n", metricName, getValue(metric)));
    }

    private static Object getValue(Metric metric) {
        if (metric instanceof Gauge) {
            return ((Gauge<?>) metric).getValue();
        } else if (metric instanceof Counter) {
            return ((Counter) metric).getCount();
        }
        return metric;
    }

    @Test
    void registerCacheMetrics() {
        Cache<Integer, String> cache =
                Caffeine.newBuilder().recordStats().maximumSize(2).build();
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

        assertThat(metricRegistry.getGauges().get("test.cache.hit.count"))
                .isNotNull()
                .extracting(Gauge::getValue)
                .isEqualTo(0L);
        assertThat(metricRegistry.getGauges().get("test.cache.miss.count"))
                .isNotNull()
                .extracting(Gauge::getValue)
                .isEqualTo(0L);

        assertThat(cache.get(0, mapping)).isEqualTo("0");
        assertThat(cache.get(1, mapping)).isEqualTo("1");
        assertThat(cache.get(2, mapping)).isEqualTo("2");
        assertThat(cache.get(1, mapping)).isEqualTo("1");

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(metricRegistry.getGauges().get("test.cache.request.count"))
                        .isNotNull()
                        .extracting(Gauge::getValue)
                        .isEqualTo(4L));

        assertThat(metricRegistry.getGauges().get("test.cache.hit.count"))
                .isNotNull()
                .extracting(Gauge::getValue)
                .isEqualTo(1L);
        assertThat(metricRegistry.getGauges().get("test.cache.miss.count"))
                .isNotNull()
                .extracting(Gauge::getValue)
                .isEqualTo(3L);

        assertThat(metricRegistry.getCounters())
                .extractingByKey("test.cache.stats.disabled")
                .isNull();
    }

    @Test
    void registerCacheTaggedMetrics() {
        Cache<Integer, String> cache =
                Caffeine.newBuilder().recordStats().maximumSize(2).build();
        CaffeineCacheStats.registerCache(taggedMetricRegistry, cache, "test");
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

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertGauge(taggedMetricRegistry, "cache.request.count").isEqualTo(0L);
            assertGauge(taggedMetricRegistry, "cache.hit.count").isEqualTo(0L);
            assertGauge(taggedMetricRegistry, "cache.miss.count").isEqualTo(0L);
        });

        assertThat(cache.get(0, mapping)).isEqualTo("0");
        assertThat(cache.get(1, mapping)).isEqualTo("1");
        assertThat(cache.get(2, mapping)).isEqualTo("2");
        assertThat(cache.get(1, mapping)).isEqualTo("1");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // await to avoid flakes as gauges may be memoized
            assertGauge(taggedMetricRegistry, "cache.request.count").isEqualTo(4L);
            assertGauge(taggedMetricRegistry, "cache.hit.count").isEqualTo(1L);
            assertGauge(taggedMetricRegistry, "cache.miss.count").isEqualTo(3L);
            assertGauge(taggedMetricRegistry, "cache.hit.ratio").isEqualTo(0.25);

            assertThat(taggedMetricRegistry.getMetrics())
                    .extractingByKey(MetricName.builder()
                            .safeName("cache.stats.disabled")
                            .putSafeTags("cache", "test")
                            .build())
                    .isNull();
        });
    }

    @Test
    void registerCacheWithoutRecordingStats() {
        Cache<Integer, String> cache = Caffeine.newBuilder().build();
        CaffeineCacheStats.registerCache(metricRegistry, cache, "test");
        String disabledMetricName = "test.cache.stats.disabled";
        assertThat(metricRegistry.getCounters())
                .hasSize(1)
                .containsOnlyKeys(disabledMetricName)
                .extractingByKey(disabledMetricName)
                .isInstanceOf(Counter.class)
                .extracting(Counter::getCount)
                .isEqualTo(1L);
    }

    @Test
    void registerCacheWithoutRecordingStatsTagged() {
        Cache<Integer, String> cache = Caffeine.newBuilder().build();
        CaffeineCacheStats.registerCache(taggedMetricRegistry, cache, "test");
        MetricName disabledMetricName = MetricName.builder()
                .safeName("cache.stats.disabled")
                .putSafeTags("cache", "test")
                .build();
        assertThat(taggedMetricRegistry.getMetrics())
                .hasSize(1)
                .containsOnlyKeys(disabledMetricName)
                .extractingByKey(disabledMetricName)
                .isInstanceOf(Counter.class)
                .asInstanceOf(type(Counter.class))
                .extracting(Counter::getCount)
                .isEqualTo(1L);
    }

    @Test
    void registerTaggedMetrics() {
        CacheStats cacheStats = CacheStats.of(taggedMetricRegistry, "test");
        Cache<Integer, String> cache = cacheStats.register(Caffeine.newBuilder()
                .recordStats(cacheStats.recorder())
                .maximumSize(2)
                .build());
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

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertGauge(taggedMetricRegistry, "cache.request.count").isEqualTo(0L);
            assertCounter(taggedMetricRegistry, "cache.hit.count").isEqualTo(0L);
            assertCounter(taggedMetricRegistry, "cache.miss.count").isEqualTo(0L);
        });

        assertThat(cache.get(0, mapping)).isEqualTo("0");
        assertThat(cache.get(1, mapping)).isEqualTo("1");
        assertThat(cache.get(2, mapping)).isEqualTo("2");
        assertThat(cache.get(1, mapping)).isEqualTo("1");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // await to avoid flakes as gauges may be memoized
            assertGauge(taggedMetricRegistry, "cache.request.count").isEqualTo(4L);
            assertCounter(taggedMetricRegistry, "cache.hit.count").isEqualTo(1L);
            assertCounter(taggedMetricRegistry, "cache.miss.count").isEqualTo(3L);
            assertGauge(taggedMetricRegistry, "cache.hit.ratio").isEqualTo(0.25);

            assertThat(taggedMetricRegistry.getMetrics())
                    .extractingByKey(MetricName.builder()
                            .safeName("cache.stats.disabled")
                            .putSafeTags("cache", "test")
                            .build())
                    .isNull();
        });
    }

    @Test
    void registerLoadingTaggedMetrics() {
        CacheStats cacheStats = CacheStats.of(taggedMetricRegistry, "test");
        LoadingCache<Integer, String> cache = cacheStats.register(Caffeine.newBuilder()
                .recordStats(cacheStats.recorder())
                .maximumSize(2)
                .build(mapping::apply));
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

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertGauge(taggedMetricRegistry, "cache.request.count").isEqualTo(0L);
            assertCounter(taggedMetricRegistry, "cache.hit.count").isEqualTo(0L);
            assertCounter(taggedMetricRegistry, "cache.miss.count").isEqualTo(0L);
        });

        assertThat(cache.get(0)).isEqualTo("0");
        assertThat(cache.get(1)).isEqualTo("1");
        assertThat(cache.get(2)).isEqualTo("2");
        assertThat(cache.get(1)).isEqualTo("1");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // await to avoid flakes as gauges may be memoized
            assertGauge(taggedMetricRegistry, "cache.request.count").isEqualTo(4L);
            assertCounter(taggedMetricRegistry, "cache.hit.count").isEqualTo(1L);
            assertCounter(taggedMetricRegistry, "cache.miss.count").isEqualTo(3L);
            assertGauge(taggedMetricRegistry, "cache.hit.ratio").isEqualTo(0.25);
            assertThat(taggedMetricRegistry.getMetrics())
                    .extractingByKey(MetricName.builder()
                            .safeName("cache.stats.disabled")
                            .putSafeTags("cache", "test")
                            .build())
                    .isNull();
        });
    }

    static AbstractObjectAssert<?, Object> assertGauge(TaggedMetricRegistry taggedMetricRegistry, String name) {
        return assertMetric(taggedMetricRegistry, Gauge.class, name).extracting(Gauge::getValue);
    }

    static AbstractObjectAssert<?, Long> assertCounter(TaggedMetricRegistry taggedMetricRegistry, String name) {
        return assertMetric(taggedMetricRegistry, Counter.class, name).extracting(Counter::getCount);
    }

    static <T extends Metric> ObjectAssert<T> assertMetric(
            TaggedMetricRegistry taggedMetricRegistry, Class<T> clazz, String name) {
        T metric = getMetric(taggedMetricRegistry, clazz, name);
        return assertThat(metric)
                .as("metric '%s': '%s'", name, metric)
                .isNotNull()
                .asInstanceOf(type(clazz));
    }

    private static <T extends Metric> T getMetric(TaggedMetricRegistry metrics, Class<T> clazz, String name) {
        Optional<Entry<MetricName, Metric>> metric = metrics.getMetrics().entrySet().stream()
                .filter(e -> name.equals(e.getKey().safeName()))
                .filter(e -> clazz.isInstance(e.getValue()))
                .findFirst();
        if (metric.isEmpty()) {
            Map<String, Set<String>> metricNameToType = Multimaps.asMap(metrics.getMetrics().entrySet().stream()
                    .filter(e -> Objects.nonNull(e.getKey()))
                    .filter(e -> Objects.nonNull(e.getValue()))
                    .collect(ImmutableSetMultimap.toImmutableSetMultimap(
                            e -> e.getKey().safeName(), e -> Optional.ofNullable(e.getValue())
                                    .map(x -> x.getClass().getCanonicalName())
                                    .orElse(""))));

            assertThat(metricNameToType)
                    .containsKey(name)
                    .extractingByKey(name)
                    .asInstanceOf(collection(String.class))
                    .contains(clazz.getCanonicalName());

            assertThat(metric)
                    .as(
                            "Metric named '%s' of type '%s' should exist but was not found in [%s]",
                            name, clazz.getCanonicalName(), metricNameToType.keySet())
                    .isPresent();
        }
        return clazz.cast(metric.orElseThrow().getValue());
    }
}
