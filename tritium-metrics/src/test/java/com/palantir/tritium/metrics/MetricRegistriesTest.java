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

package com.palantir.tritium.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;
import static org.mockito.Mockito.mock;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.SortedMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mpierce.metrics.reservoir.hdrhistogram.HdrHistogramReservoir;

@SuppressWarnings({
    "BanGuavaCaches", // this implementation is explicitly for Guava caches
    "JdkObsolete", // SortedMap is part of Metrics API
    "NullAway"
})
final class MetricRegistriesTest {

    private MetricRegistry metrics = new MetricRegistry();
    private final TaggedMetricRegistry taggedMetricRegistry = new DefaultTaggedMetricRegistry();
    private final TestClock clock = new TestClock();

    private final LoadingCache<Integer, String> cache = CacheBuilder.newBuilder()
            .maximumSize(2)
            .recordStats()
            .build(new CacheLoader<Integer, String>() {
                @Override
                public String load(@Nonnull Integer key) {
                    return String.valueOf(key);
                }
            });

    @AfterEach
    void after() {
        report(metrics);
    }

    @Test
    void defaultMetrics() {
        metrics = MetricRegistries.createWithHdrHistogramReservoirs();
        assertThat(metrics.getGauges()).hasSize(3);
        assertThat(metrics.getGauges()).containsKey(MetricRegistries.RESERVOIR_TYPE_METRIC_NAME);
        assertThat(metrics.getGauges()
                        .get(MetricRegistries.RESERVOIR_TYPE_METRIC_NAME)
                        .getValue())
                .isEqualTo(HdrHistogramReservoir.class.getName());
        assertThat(metrics.getGauges().keySet())
                .containsExactly(
                        MetricRegistries.RESERVOIR_TYPE_METRIC_NAME,
                        "com.palantir.tritium.metrics.snapshot.begin",
                        "com.palantir.tritium.metrics.snapshot.now");
        report(metrics);
    }

    @Test
    void testHdrHistogram() {
        metrics = MetricRegistries.createWithHdrHistogramReservoirs();
        assertThat(metrics).isNotNull();

        Histogram histogram = metrics.histogram("histogram");
        histogram.update(42L);
        assertThat(histogram.getCount()).isOne();
        Snapshot histogramSnapshot = histogram.getSnapshot();
        assertThat(histogram.getCount()).isOne();
        assertThat(histogramSnapshot.size()).isOne();
        assertThat(histogramSnapshot.getMax()).isEqualTo(42);

        metrics.timer("timer").update(Duration.ofMillis(123));
        assertThat(metrics.timer("timer").getCount()).isOne();
    }

    @Test
    void testSlidingTimeWindowHistogram() {
        metrics = MetricRegistries.createWithSlidingTimeWindowReservoirs(1, TimeUnit.MINUTES);
        assertThat(metrics).isNotNull();

        Histogram histogram = metrics.histogram("histogram");
        histogram.update(42L);
        assertThat(histogram.getCount()).isOne();
        Snapshot histogramSnapshot = histogram.getSnapshot();
        assertThat(histogram.getCount()).isOne();
        assertThat(histogramSnapshot.size()).isOne();
        assertThat(histogramSnapshot.getMax()).isEqualTo(42);

        metrics.timer("timer").update(Duration.ofMillis(123));
        assertThat(metrics.timer("timer").getCount()).isOne();
    }

    @Test
    void testSlidingTimeWindowHistogramExpiery() {
        long window = 60;
        TimeUnit windowUnit = TimeUnit.SECONDS;

        Reservoirs.slidingTimeWindowArrayReservoir(window, windowUnit, clock);
        metrics = MetricRegistries.createWithReservoirType(
                () -> Reservoirs.slidingTimeWindowArrayReservoir(window, windowUnit, clock));
        assertThat(metrics).isNotNull();

        Histogram histogram = metrics.histogram("histogram");
        histogram.update(42L);
        assertThat(histogram.getCount()).isOne();
        Snapshot histogramSnapshot = histogram.getSnapshot();
        assertThat(histogram.getCount()).isOne();
        assertThat(histogramSnapshot.size()).isOne();
        assertThat(histogramSnapshot.getMax()).isEqualTo(42);

        clock.advance(window / 2, windowUnit);

        histogram.update(1337L);
        histogramSnapshot = histogram.getSnapshot();
        assertThat(histogram.getCount()).isEqualTo(2);
        assertThat(histogramSnapshot.size()).isEqualTo(2);
        assertThat(histogramSnapshot.getMax()).isEqualTo(1337);

        clock.advance(window / 2 + 1, windowUnit);

        histogramSnapshot = histogram.getSnapshot();
        assertThat(histogram.getCount()).isEqualTo(2);
        assertThat(histogramSnapshot.size()).isOne();
        assertThat(histogramSnapshot.getMax()).isEqualTo(1337);

        clock.advance(window, windowUnit);

        histogramSnapshot = histogram.getSnapshot();
        assertThat(histogram.getCount()).isEqualTo(2);
        assertThat(histogramSnapshot.size()).isZero();

        metrics.timer("timer").update(Duration.ofMillis(123));
        assertThat(metrics.timer("timer").getCount()).isOne();
    }

    @Test
    void testDecayingHistogramReservoirs() {
        metrics = MetricRegistries.createWithReservoirType(ExponentiallyDecayingReservoir::new);
        assertThat(metrics).isNotNull();

        Histogram histogram = metrics.histogram("histogram");
        histogram.update(42L);
        assertThat(histogram.getCount()).isOne();
        Snapshot histogramSnapshot = histogram.getSnapshot();
        assertThat(histogram.getCount()).isOne();
        assertThat(histogramSnapshot.size()).isOne();
        assertThat(histogramSnapshot.getMax()).isEqualTo(42);

        metrics.timer("timer").update(Duration.ofMillis(123));
        assertThat(metrics.timer("timer").getCount()).isOne();
    }

    @Test
    void testLockFreeDecayingHistogramReservoirs() {
        metrics = MetricRegistries.createWithLockFreeExponentiallyDecayingReservoirs();
        assertThat(metrics).isNotNull();

        Histogram histogram = metrics.histogram("histogram");
        histogram.update(42L);
        assertThat(histogram.getCount()).isOne();
        Snapshot histogramSnapshot = histogram.getSnapshot();
        assertThat(histogram.getCount()).isOne();
        assertThat(histogramSnapshot.size()).isOne();
        assertThat(histogramSnapshot.getMax()).isEqualTo(42);

        metrics.timer("timer").update(Duration.ofMillis(123));
        assertThat(metrics.timer("timer").getCount()).isOne();
    }

    @Test
    void testRegisterCache() {
        MetricRegistries.registerCache(metrics, cache, "test", clock);

        assertThat(metrics.getGauges(MetricRegistries.metricsPrefixedBy("test")).keySet())
                .containsExactlyInAnyOrder(
                        "test.cache.estimated.size",
                        "test.cache.eviction.count",
                        "test.cache.hit.count",
                        "test.cache.hit.ratio",
                        "test.cache.load.average.millis",
                        "test.cache.load.failure.count",
                        "test.cache.load.success.count",
                        "test.cache.miss.count",
                        "test.cache.miss.ratio",
                        "test.cache.request.count");

        cache.getUnchecked(1);
        cache.getUnchecked(2);
        cache.getUnchecked(1);

        @SuppressWarnings("rawtypes")
        SortedMap<String, Gauge> gauges = metrics.getGauges();
        waitAtMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(gauges.get("test.cache.request.count").getValue()).isEqualTo(3L);
            assertThat(gauges.get("test.cache.hit.count").getValue()).isEqualTo(1L);
            assertThat(gauges.get("test.cache.hit.ratio").getValue()).isEqualTo(1.0 / 3.0);
            assertThat(gauges.get("test.cache.miss.count").getValue()).isEqualTo(2L);
            assertThat(gauges.get("test.cache.miss.ratio").getValue()).isEqualTo(2.0 / 3.0);
            assertThat(gauges.get("test.cache.estimated.size").getValue()).isEqualTo(2L);
            assertThat(gauges.get("test.cache.eviction.count").getValue()).isEqualTo(0L);
            assertThat(gauges.get("test.cache.load.average.millis").getValue()).isNotEqualTo(5.0 / 3.0);
            assertThat(gauges.get("test.cache.load.failure.count").getValue()).isEqualTo(0L);
            assertThat(gauges.get("test.cache.load.success.count").getValue()).isEqualTo(2L);
        });
    }

    @Test
    @SuppressWarnings("deprecation") // explicitly testing deprecated non-tagged metric registry
    void testRegisterCacheReplacement() {
        Cache<?, ?> cache1 = CacheBuilder.newBuilder().build();
        MetricRegistries.registerCache(metrics, cache1, "test");

        Cache<?, ?> cache2 = CacheBuilder.newBuilder().build();
        MetricRegistries.registerCache(metrics, cache2, "test");
    }

    @Test
    @SuppressWarnings("deprecation") // explicitly testing deprecated non-tagged metric registry
    void testNoStats() {
        MetricRegistries.registerCache(metrics, cache, "test");

        assertThat(metrics.getGauges(MetricRegistries.metricsPrefixedBy("test")).keySet())
                .containsExactlyInAnyOrder(
                        "test.cache.estimated.size",
                        "test.cache.eviction.count",
                        "test.cache.hit.count",
                        "test.cache.hit.ratio",
                        "test.cache.load.average.millis",
                        "test.cache.load.failure.count",
                        "test.cache.load.success.count",
                        "test.cache.miss.count",
                        "test.cache.miss.ratio",
                        "test.cache.request.count");

        @SuppressWarnings("rawtypes")
        SortedMap<String, Gauge> gauges = metrics.getGauges();
        waitAtMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(gauges.get("test.cache.request.count").getValue()).isEqualTo(0L);
            assertThat(gauges.get("test.cache.hit.count").getValue()).isEqualTo(0L);
            assertThat(gauges.get("test.cache.hit.ratio").getValue()).isEqualTo(Double.NaN);
            assertThat(gauges.get("test.cache.miss.count").getValue()).isEqualTo(0L);
            assertThat(gauges.get("test.cache.miss.ratio").getValue()).isEqualTo(Double.NaN);
            assertThat(gauges.get("test.cache.eviction.count").getValue()).isEqualTo(0L);
            assertThat(gauges.get("test.cache.load.average.millis").getValue()).isEqualTo(0.0d);
            assertThat(gauges.get("test.cache.load.failure.count").getValue()).isEqualTo(0L);
            assertThat(gauges.get("test.cache.load.success.count").getValue()).isEqualTo(0L);
        });
    }

    @Test
    void registerCacheTaggedMetrics() throws ExecutionException {
        MetricRegistries.registerCache(taggedMetricRegistry, cache, "test");
        assertThat(taggedMetricRegistry.getMetrics().keySet())
                .extracting(MetricName::safeName)
                .contains(
                        "cache.estimated.size",
                        "cache.request.count",
                        "cache.hit.count",
                        "cache.hit.ratio",
                        "cache.miss.count",
                        "cache.miss.ratio",
                        "cache.eviction.count",
                        "cache.load.success.count",
                        "cache.load.failure.count",
                        "cache.load.average.millis");

        // Function<Integer, String> mapping = String::valueOf;
        assertThat(cache.get(0)).isEqualTo("0");
        assertThat(cache.get(1)).isEqualTo("1");
        assertThat(cache.get(2)).isEqualTo("2");
        assertThat(cache.get(1)).isEqualTo("1");

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(getMetric(taggedMetricRegistry, Gauge.class, "cache.request.count")
                                .getValue())
                        .isEqualTo(4L));

        assertThat(getMetric(taggedMetricRegistry, Gauge.class, "cache.hit.count")
                        .getValue())
                .isEqualTo(1L);
        assertThat(getMetric(taggedMetricRegistry, Gauge.class, "cache.miss.count")
                        .getValue())
                .isEqualTo(3L);
        assertThat(getMetric(taggedMetricRegistry, Gauge.class, "cache.hit.ratio")
                        .getValue())
                .isEqualTo(0.25);
    }

    @Test
    void testGetOrAddDuplicate() {
        Counter mockMetric = mock(Counter.class);
        MetricBuilder<Counter> metricBuilder = new MetricBuilder<Counter>() {
            @Override
            public Counter newMetric() {
                return mockMetric;
            }

            @Override
            public boolean isInstance(Metric metric) {
                return mockMetric == metric;
            }
        };

        assertThat(MetricRegistries.getOrAdd(metrics, "test", metricBuilder)).isSameAs(mockMetric);
        assertThat(MetricRegistries.getOrAdd(metrics, "test", metricBuilder)).isSameAs(mockMetric);
    }

    @Test
    void testInvalidGetOrAdd() {
        HistogramMetricBuilder histogramMetricBuilder = new HistogramMetricBuilder(Reservoirs::hdrHistogramReservoir);
        MetricRegistries.getOrAdd(metrics, "histogram", histogramMetricBuilder);

        TimerMetricBuilder timerMetricBuilder = new TimerMetricBuilder(Reservoirs::hdrHistogramReservoir);
        assertThatThrownBy(() -> MetricRegistries.getOrAdd(metrics, "histogram", timerMetricBuilder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Metric name already used for different metric type")
                .hasMessageContaining("metricName=histogram")
                .hasMessageContaining("existingMetricType=com.codahale.metrics.Histogram")
                .hasMessageContaining("newMetricType=com.codahale.metrics.Timer");
    }

    @Test
    void testRegisterSafeDuplicateIgnored() {
        Metric mockMetric1 = mock(Counter.class, "metric1");
        assertThat(MetricRegistries.registerSafe(metrics, "test", mockMetric1)).isSameAs(mockMetric1);
        assertThat(metrics.getMetrics()).containsEntry("test", mockMetric1);

        Metric mockMetric2 = mock(Counter.class, "metric2");
        assertThat(MetricRegistries.registerSafe(metrics, "test", mockMetric2)).isSameAs(mockMetric1);
        assertThat(metrics.getMetrics()).containsEntry("test", mockMetric1);
    }

    @Test
    void testRegisterWithReplacement() {
        Metric mockMetric1 = mock(Metric.class, "metric1");
        assertThat(MetricRegistries.registerWithReplacement(metrics, "test", mockMetric1))
                .isEqualTo(mockMetric1);
        assertThat(metrics.getMetrics()).containsEntry("test", mockMetric1);

        Metric mockMetric2 = mock(Metric.class, "metric2");
        assertThat(MetricRegistries.registerWithReplacement(metrics, "test", mockMetric2))
                .isEqualTo(mockMetric2);
        assertThat(metrics.getMetrics()).containsEntry("test", mockMetric2);
    }

    @Test
    void testInvalidReregistration() {
        Metric metric = metrics.counter("counter");
        assertThat(MetricRegistries.registerSafe(metrics, "test", metric)).isSameAs(metric);
        assertThatThrownBy(() -> MetricRegistries.registerSafe(metrics, "test", metrics.histogram("histogram")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(
                        "Metric already registered at this name that implements a different set of interfaces: "
                                + "{name=test, existingMetric=com.codahale.metrics.Counter");
    }

    @Test
    void testMetricsPrefixedBy() {
        MetricFilter metricFilter = MetricRegistries.metricsPrefixedBy("test");

        Metric metric = mock(Metric.class);
        assertThat(metricFilter.matches("test", metric)).isTrue();
        assertThat(metricFilter.matches("test", null)).isTrue();
        assertThat(metricFilter.matches("test.foo", metric)).isTrue();
        assertThat(metricFilter.matches("testing", metric)).isTrue();
        assertThat(metricFilter.matches("bar", metric)).isFalse();
        assertThat(metricFilter.matches("bar", null)).isFalse();
    }

    @Test
    void testNullPrefixMetricsPrefixedBy() {
        assertThatThrownBy(() -> MetricRegistries.metricsPrefixedBy(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testMetricsMatching() {
        MetricFilter palantirFilter = (name, _metric) -> name.startsWith("test");

        metrics.counter("test.a");
        metrics.timer("test.b");
        metrics.counter("non.matching");

        SortedMap<String, Metric> metricsMatching = MetricRegistries.metricsMatching(metrics, palantirFilter);
        assertThat(metricsMatching).hasSize(2);
        assertThat(metricsMatching.keySet()).containsExactly("test.a", "test.b");
        assertThat(metricsMatching.values()).containsExactly(metrics.counter("test.a"), metrics.timer("test.b"));
    }

    @Test
    void testTimestamp() {
        String isoTimestamp = MetricRegistries.nowIsoTimestamp();
        assertParsesTimestamp(isoTimestamp);
        assertThat(ZonedDateTime.from(DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(isoTimestamp)))
                .isBeforeOrEqualTo(ZonedDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void problematicTimestamps() {
        assertParsesTimestamp("2019-03-30T02:06:35.450Z");
        assertParsesTimestamp("2019-03-30T02:06:35.045Z");
        assertParsesTimestamp("2019-03-29T23:38:51.920Z");
        assertParsesTimestamp("2019-03-29T23:38:51.092Z");
    }

    @Test
    void testGarbageCollectionMetrics() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        MetricRegistries.registerGarbageCollection(registry);
        assertThat(registry.getMetrics().keySet())
                .filteredOn(metricName -> metricName.safeName().startsWith("jvm.gc.time")
                        || metricName.safeName().startsWith("jvm.gc.count"))
                .allSatisfy(metricName -> assertThat(metricName.safeTags())
                        .containsOnlyKeys("collector", "libraryName", "libraryVersion", "javaVersion"));

        assertThat(registry.getMetrics().keySet())
                .filteredOn(metricName -> metricName.safeName().equals("jvm.gc.finalizer.queue.size"))
                .hasSize(1)
                .allSatisfy(metricName ->
                        assertThat(metricName.safeTags()).containsOnlyKeys("libraryName", "libraryVersion"));
    }

    @Test
    void testMemoryPoolMetrics() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        MetricRegistries.registerMemoryPools(registry);
        assertThat(registry.getMetrics().keySet()).allSatisfy(metricName -> {
            assertThat(metricName.safeName()).matches("jvm\\.memory\\.pools\\..+");
            assertThat(metricName.safeTags())
                    .containsOnlyKeys("memoryPool", "libraryName", "libraryVersion", "javaVersion");
        });
        // n.b. Test does not check for 'used-after-gc' because it depends on the runtime
        assertThat(registry.getMetrics().keySet())
                .extracting(MetricName::safeName)
                .contains(
                        "jvm.memory.pools.max",
                        "jvm.memory.pools.used",
                        "jvm.memory.pools.committed",
                        "jvm.memory.pools.init",
                        "jvm.memory.pools.usage");
    }

    @Test
    void testRegisterAll() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        Gauge<Long> gauge = () -> 1L;
        Meter meter = new Meter();
        Histogram histogram = new Histogram(new ExponentiallyDecayingReservoir());
        Counter counter = new Counter();
        Timer timer = new Timer();
        MetricSet metricSet = () -> ImmutableMap.<String, Metric>builder()
                .put("gauge", gauge)
                .put("meter", meter)
                .put("histogram", histogram)
                .put("counter", counter)
                .put("timer", timer)
                .put("set", (MetricSet) () -> ImmutableMap.of("gauge", gauge))
                .build();
        MetricRegistries.registerAll(registry, "tritium", metricSet);
        assertThat(registry.getMetrics())
                .containsExactlyInAnyOrderEntriesOf(ImmutableMap.<MetricName, Metric>builder()
                        .put(simpleName("tritium.gauge"), gauge)
                        .put(simpleName("tritium.meter"), meter)
                        .put(simpleName("tritium.histogram"), histogram)
                        .put(simpleName("tritium.counter"), counter)
                        .put(simpleName("tritium.timer"), timer)
                        .put(simpleName("tritium.set.gauge"), gauge)
                        .build());
    }

    @Test
    void testRegisterAllUnknownType() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        MetricSet metricSet = () -> ImmutableMap.of("unknown", new Metric() {});
        assertThatThrownBy(() -> MetricRegistries.registerAll(registry, "prefix", metricSet))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MetricName simpleName(String name) {
        return MetricName.builder().safeName(name).build();
    }

    @SuppressWarnings("SameParameterValue")
    private static <T extends Metric> T getMetric(TaggedMetricRegistry metrics, Class<T> clazz, String name) {
        return clazz.cast(metrics.getMetrics().entrySet().stream()
                .filter(e -> name.equals(e.getKey().safeName()))
                .filter(e -> clazz.isInstance(e.getValue()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such metric " + name))
                .getValue());
    }

    private static void assertParsesTimestamp(String timestamp) {
        assertThat(ZonedDateTime.from(DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(timestamp)))
                .isBeforeOrEqualTo(ZonedDateTime.now(ZoneOffset.UTC));
    }

    private static void report(MetricRegistry metrics) {
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics).build();
        reporter.report();
        reporter.stop();
    }
}
