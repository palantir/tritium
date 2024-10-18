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

import static com.palantir.logsafe.testing.Assertions.assertThatLoggableExceptionThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.awaitility.Awaitility.await;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tritium.metrics.caffeine.CacheMetrics.Request_Result;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.assertj.core.api.AbstractObjectAssert;
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

        assertThat(metricRegistry.getMeters())
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
            assertGauge(
                            taggedMetricRegistry,
                            MetricName.builder()
                                    .safeName("cache.request.count")
                                    .putSafeTags("cache", "test")
                                    .build())
                    .isEqualTo(0L);
            assertGauge(
                            taggedMetricRegistry,
                            MetricName.builder()
                                    .safeName("cache.hit.count")
                                    .putSafeTags("cache", "test")
                                    .build())
                    .isEqualTo(0L);
            assertGauge(
                            taggedMetricRegistry,
                            MetricName.builder()
                                    .safeName("cache.miss.count")
                                    .putSafeTags("cache", "test")
                                    .build())
                    .isEqualTo(0L);
        });

        assertThat(cache.get(0, mapping)).isEqualTo("0");
        assertThat(cache.get(1, mapping)).isEqualTo("1");
        assertThat(cache.get(2, mapping)).isEqualTo("2");
        assertThat(cache.get(1, mapping)).isEqualTo("1");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // await to avoid flakes as gauges may be memoized
            assertGauge(
                            taggedMetricRegistry,
                            MetricName.builder()
                                    .safeName("cache.request.count")
                                    .putSafeTags("cache", "test")
                                    .build())
                    .isEqualTo(4L);
            assertGauge(
                            taggedMetricRegistry,
                            MetricName.builder()
                                    .safeName("cache.hit.count")
                                    .putSafeTags("cache", "test")
                                    .build())
                    .isEqualTo(1L);
            assertGauge(
                            taggedMetricRegistry,
                            MetricName.builder()
                                    .safeName("cache.miss.count")
                                    .putSafeTags("cache", "test")
                                    .build())
                    .isEqualTo(3L);
            assertGauge(
                            taggedMetricRegistry,
                            MetricName.builder()
                                    .safeName("cache.hit.ratio")
                                    .putSafeTags("cache", "test")
                                    .build())
                    .isEqualTo(0.25);

            assertThat(taggedMetricRegistry.getMetrics())
                    .extractingByKey(CacheMetrics.statsDisabledMetricName("test"))
                    .isNull();
        });
    }

    @Test
    void registerCacheWithoutRecordingStats() {
        Cache<Integer, String> cache = Caffeine.newBuilder().build();
        CaffeineCacheStats.registerCache(metricRegistry, cache, "test");
        String disabledMetricName = "test.cache.stats.disabled";
        assertThat(metricRegistry.getMeters())
                .hasSize(1)
                .containsOnlyKeys(disabledMetricName)
                .extractingByKey(disabledMetricName)
                .isInstanceOf(Meter.class)
                .extracting(Meter::getCount)
                .isEqualTo(1L);
    }

    @Test
    void registerCacheWithoutRecordingStatsTagged() {
        Cache<Integer, String> cache = Caffeine.newBuilder().build();
        CaffeineCacheStats.registerCache(taggedMetricRegistry, cache, "test");
        MetricName disabledMetricName = CacheMetrics.statsDisabledMetricName("test");
        assertThat(taggedMetricRegistry.getMetrics())
                .hasSize(1)
                .containsOnlyKeys(disabledMetricName)
                .extractingByKey(disabledMetricName)
                .isInstanceOf(Meter.class)
                .asInstanceOf(type(Meter.class))
                .extracting(Meter::getCount)
                .isEqualTo(1L);
    }

    @Test
    void registerTaggedMetrics() {
        Cache<Integer, String> cache = CacheStats.of(taggedMetricRegistry, "test")
                .register(stats ->
                        Caffeine.newBuilder().recordStats(stats).maximumSize(2).build());

        assertThat(taggedMetricRegistry.getMetrics().keySet())
                .extracting(MetricName::safeName)
                .containsExactlyInAnyOrder(
                        "cache.request", // hit
                        "cache.request", // miss
                        "cache.eviction", // RemovalCause.EXPLICIT
                        "cache.eviction", // RemovalCause.REPLACED
                        "cache.eviction", // RemovalCause.COLLECTED
                        "cache.eviction", // RemovalCause.EXPIRED
                        "cache.eviction", // RemovalCause.SIZE
                        "cache.eviction.weight", // RemovalCause.EXPLICIT
                        "cache.eviction.weight", // RemovalCause.REPLACED
                        "cache.eviction.weight", // RemovalCause.COLLECTED
                        "cache.eviction.weight", // RemovalCause.EXPIRED
                        "cache.eviction.weight", // RemovalCause.SIZE
                        "cache.load", // success
                        "cache.load", // failure
                        "cache.estimated.size",
                        "cache.weighted.size",
                        "cache.maximum.size");

        CacheMetrics cacheMetrics = CacheMetrics.of(taggedMetricRegistry);
        assertThat(cacheMetrics.eviction().cache("test").cause("SIZE").build().getCount())
                .isZero();
        assertThat(cacheMetrics
                        .request()
                        .cache("test")
                        .result(Request_Result.HIT)
                        .build()
                        .getCount())
                .isZero();
        assertThat(cacheMetrics
                        .request()
                        .cache("test")
                        .result(Request_Result.MISS)
                        .build()
                        .getCount())
                .isZero();

        assertThat(cache.get(0, mapping)).isEqualTo("0");
        assertThat(cache.get(1, mapping)).isEqualTo("1");
        assertThat(cache.get(2, mapping)).isEqualTo("2");
        assertThat(cache.get(1, mapping)).isEqualTo("1");

        assertThat(cacheMetrics
                        .request()
                        .cache("test")
                        .result(Request_Result.HIT)
                        .build()
                        .getCount())
                .isOne();
        assertThat(cacheMetrics
                        .request()
                        .cache("test")
                        .result(Request_Result.MISS)
                        .build()
                        .getCount())
                .isEqualTo(3);

        cache.cleanUp(); // force eviction processing
        assertThat(cacheMetrics.eviction().cache("test").cause("SIZE").build().getCount())
                .isOne();

        assertThat(taggedMetricRegistry.getMetrics())
                .extractingByKey(CacheMetrics.statsDisabledMetricName("test"))
                .isNull();
    }

    @Test
    void registerLoadingTaggedMetrics() {
        LoadingCache<Integer, String> cache = CacheStats.of(taggedMetricRegistry, "test")
                .register(stats ->
                        Caffeine.newBuilder().recordStats(stats).maximumSize(2).build(mapping::apply));
        assertThat(taggedMetricRegistry.getMetrics().keySet())
                .extracting(MetricName::safeName)
                .containsExactlyInAnyOrder(
                        "cache.request", // hit
                        "cache.request", // miss
                        "cache.eviction", // RemovalCause.EXPLICIT
                        "cache.eviction", // RemovalCause.REPLACED
                        "cache.eviction", // RemovalCause.COLLECTED
                        "cache.eviction", // RemovalCause.EXPIRED
                        "cache.eviction", // RemovalCause.SIZE
                        "cache.eviction.weight", // RemovalCause.EXPLICIT
                        "cache.eviction.weight", // RemovalCause.REPLACED
                        "cache.eviction.weight", // RemovalCause.COLLECTED
                        "cache.eviction.weight", // RemovalCause.EXPIRED
                        "cache.eviction.weight", // RemovalCause.SIZE
                        "cache.load", // success
                        "cache.load", // failure
                        "cache.estimated.size",
                        "cache.weighted.size",
                        "cache.maximum.size");

        CacheMetrics cacheMetrics = CacheMetrics.of(taggedMetricRegistry);
        assertThat(cacheMetrics.eviction().cache("test").cause("SIZE").build().getCount())
                .isZero();
        assertThat(cacheMetrics
                        .request()
                        .cache("test")
                        .result(Request_Result.HIT)
                        .build()
                        .getCount())
                .isZero();
        assertThat(cacheMetrics
                        .request()
                        .cache("test")
                        .result(Request_Result.MISS)
                        .build()
                        .getCount())
                .isZero();

        assertThat(cache.get(0)).isEqualTo("0");
        assertThat(cache.get(1)).isEqualTo("1");
        assertThat(cache.get(2)).isEqualTo("2");
        assertThat(cache.get(1)).isEqualTo("1");

        assertThat(cacheMetrics
                        .request()
                        .cache("test")
                        .result(Request_Result.HIT)
                        .build()
                        .getCount())
                .isOne();
        assertThat(cacheMetrics
                        .request()
                        .cache("test")
                        .result(Request_Result.MISS)
                        .build()
                        .getCount())
                .isEqualTo(3);

        cache.cleanUp(); // force eviction processing
        assertThat(cacheMetrics.eviction().cache("test").cause("SIZE").build().getCount())
                .isOne();
    }

    @Test
    void registerWithoutStatsRecording() {
        CacheStats cacheStats = CacheStats.of(taggedMetricRegistry, "test");

        assertThatLoggableExceptionThrownBy(() ->
                        cacheStats.register(_stats -> Caffeine.newBuilder().build()))
                .isInstanceOf(SafeIllegalStateException.class)
                .hasLogMessage("Registered cache is not recording stats. Registered caches must enabled stats "
                        + "recording with .recordStats(stats).")
                .hasNoArgs();
    }

    static AbstractObjectAssert<?, Object> assertGauge(
            TaggedMetricRegistry taggedMetricRegistry, MetricName metricName) {
        return assertThat(taggedMetricRegistry.gauge(metricName)).get().extracting(Gauge::getValue);
    }
}
