/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mpierce.metrics.reservoir.hdrhistogram.HdrHistogramReservoir;

@RunWith(MockitoJUnitRunner.class)
public class MetricRegistriesTest {

    private MetricRegistry metrics = new MetricRegistry();
    private final TestClock clock = new TestClock();

    @Mock
    private LoadingCache<Integer, String> cache;

    @After
    public void after() {
        report(metrics);
    }

    @Test
    public void defaultMetrics() {
        metrics = MetricRegistries.createWithHdrHistogramReservoirs();
        assertThat(metrics.getGauges().size()).isEqualTo(3);
        assertThat(metrics.getGauges()).containsKey(MetricRegistries.RESERVOIR_TYPE_METRIC_NAME);
        assertThat(metrics.getGauges().get(MetricRegistries.RESERVOIR_TYPE_METRIC_NAME).getValue())
                .isEqualTo(HdrHistogramReservoir.class.getName());
        assertThat(metrics.getGauges().keySet()).containsExactly(
                MetricRegistries.RESERVOIR_TYPE_METRIC_NAME,
                "com.palantir.tritium.metrics.snapshot.begin",
                "com.palantir.tritium.metrics.snapshot.now"
        );
        report(metrics);
    }

    @Test
    public void testHdrHistogram() {
        metrics = MetricRegistries.createWithHdrHistogramReservoirs();
        assertThat(metrics).isNotNull();

        Histogram histogram = metrics.histogram("histogram");
        histogram.update(42L);
        assertThat(histogram.getCount()).isEqualTo(1);
        Snapshot histogramSnapshot = histogram.getSnapshot();
        assertThat(histogram.getCount()).isEqualTo(1);
        assertThat(histogramSnapshot.size()).isEqualTo(1);
        assertThat(histogramSnapshot.getMax()).isEqualTo(42);

        metrics.timer("timer").update(123, TimeUnit.MILLISECONDS);
        assertThat(metrics.timer("timer").getCount()).isEqualTo(1);
    }

    @Test
    public void testRegisterCache() {
        MetricRegistries.registerCache(metrics, cache, "test", clock);
        assertThat(metrics.getGauges(MetricRegistries.metricsPrefixedBy("test")).keySet()).containsExactlyInAnyOrder(
                "test.cache.estimated.size",
                "test.cache.eviction.count",
                "test.cache.hit.count",
                "test.cache.hit.ratio",
                "test.cache.load.failure.count",
                "test.cache.load.success.count",
                "test.cache.load.average.millis",
                "test.cache.miss.count",
                "test.cache.miss.ratio",
                "test.cache.request.count"
        );

        when(cache.stats()).thenReturn(new CacheStats(1, 2, 3, 4, 5, 6));
        when(cache.size()).thenReturn(42L);

        SortedMap<String, Gauge> gauges = metrics.getGauges();
        assertThat(gauges.get("test.cache.request.count").getValue()).isEqualTo(3L);
        assertThat(gauges.get("test.cache.hit.count").getValue()).isEqualTo(1L);
        assertThat(gauges.get("test.cache.hit.ratio").getValue()).isEqualTo(1.0 / 3.0);
        assertThat(gauges.get("test.cache.miss.count").getValue()).isEqualTo(2L);
        assertThat(gauges.get("test.cache.miss.ratio").getValue()).isEqualTo(2.0 / 3.0);
        assertThat(gauges.get("test.cache.estimated.size").getValue()).isEqualTo(42L);
        assertThat(gauges.get("test.cache.eviction.count").getValue()).isEqualTo(6L);
        assertThat(gauges.get("test.cache.load.average.millis").getValue()).isNotEqualTo(5.0 / 3.0);
        assertThat(gauges.get("test.cache.load.failure.count").getValue()).isEqualTo(4L);
        assertThat(gauges.get("test.cache.load.success.count").getValue()).isEqualTo(3L);
        verify(cache, times(1)).stats();

        clock.advance(1, TimeUnit.MINUTES); // let stats snapshot cache expire

        when(cache.stats()).thenReturn(new CacheStats(11L, 12L, 13L, 14L, 15L, 16L));
        when(cache.size()).thenReturn(37L);

        gauges = metrics.getGauges();
        assertThat(gauges.get("test.cache.request.count").getValue()).isEqualTo(23L);
        assertThat(gauges.get("test.cache.hit.count").getValue()).isEqualTo(11L);
        assertThat(gauges.get("test.cache.miss.count").getValue()).isEqualTo(12L);
        assertThat(gauges.get("test.cache.eviction.count").getValue()).isEqualTo(16L);
        assertThat(gauges.get("test.cache.load.average.millis").getValue()).isNotEqualTo(15.0 / 23.0);
        assertThat(gauges.get("test.cache.load.failure.count").getValue()).isEqualTo(14L);
        assertThat(gauges.get("test.cache.load.success.count").getValue()).isEqualTo(13L);
        verify(cache, times(2)).stats();
    }

    @Test
    public void testRegisterCacheReplacement() {
        Cache cache1 = CacheBuilder.newBuilder().build();
        MetricRegistries.registerCache(metrics, cache1, "test");

        Cache cache2 = CacheBuilder.newBuilder().build();
        MetricRegistries.registerCache(metrics, cache2, "test");
    }

    @Test
    public void testNoStats() throws Exception {
        MetricRegistries.registerCache(metrics, cache, "test");

        assertThat(metrics.getGauges(MetricRegistries.metricsPrefixedBy("test")).keySet()).containsExactlyInAnyOrder(
                "test.cache.estimated.size",
                "test.cache.eviction.count",
                "test.cache.hit.count",
                "test.cache.hit.ratio",
                "test.cache.load.failure.count",
                "test.cache.load.success.count",
                "test.cache.load.average.millis",
                "test.cache.miss.count",
                "test.cache.miss.ratio",
                "test.cache.request.count"
        );

        when(cache.stats()).thenReturn(new CacheStats(0L, 0L, 0L, 0L, 0L, 0L));
        assertThat(metrics.getGauges().get("test.cache.request.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.hit.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.hit.ratio").getValue()).isEqualTo(Double.NaN);
        assertThat(metrics.getGauges().get("test.cache.miss.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.miss.ratio").getValue()).isEqualTo(Double.NaN);
        assertThat(metrics.getGauges().get("test.cache.eviction.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.load.average.millis").getValue()).isEqualTo(0.0d);
        assertThat(metrics.getGauges().get("test.cache.load.failure.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.load.success.count").getValue()).isEqualTo(0L);
    }

    @Test
    public void testGetOrAddDuplicate() {
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
    public void testInvalidGetOrAdd() {
        MetricRegistries.getOrAdd(metrics, "histogram",
                new HistogramMetricBuilder(Reservoirs.hdrHistogramReservoirSupplier()));

        assertThatThrownBy(() ->
                MetricRegistries.getOrAdd(metrics, "histogram",
                        new TimerMetricBuilder(Reservoirs.hdrHistogramReservoirSupplier())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("histogram is already used for a different type of metric for ");
    }

    @Test
    public void testRegisterSafeDuplicateIgnored() {
        Metric mockMetric1 = mock(Counter.class, "metric1");
        assertThat(MetricRegistries.registerSafe(metrics, "test", mockMetric1)).isSameAs(mockMetric1);
        assertThat(metrics.getMetrics().get("test")).isEqualTo(mockMetric1);

        Metric mockMetric2 = mock(Counter.class, "metric2");
        assertThat(MetricRegistries.registerSafe(metrics, "test", mockMetric2)).isSameAs(mockMetric1);
        assertThat(metrics.getMetrics().get("test")).isEqualTo(mockMetric1);
    }

    @Test
    public void testRegisterWithReplacement() {
        Metric mockMetric1 = mock(Metric.class, "metric1");
        assertThat(MetricRegistries.registerWithReplacement(metrics, "test", mockMetric1)).isEqualTo(mockMetric1);
        assertThat(metrics.getMetrics().get("test")).isEqualTo(mockMetric1);

        Metric mockMetric2 = mock(Metric.class, "metric2");
        assertThat(MetricRegistries.registerWithReplacement(metrics, "test", mockMetric2)).isEqualTo(mockMetric2);
        assertThat(metrics.getMetrics().get("test")).isEqualTo(mockMetric2);
    }

    @Test
    public void testInvalidReregistration() {
        Metric metric = metrics.counter("counter");
        assertThat(MetricRegistries.registerSafe(metrics, "test", metric)).isSameAs(metric);
        assertThatThrownBy(() ->
                MetricRegistries.registerSafe(metrics, "test", metrics.histogram("histogram")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(
                        "Metric already registered at this name that implements a different set of interfaces. "
                                + "Name: test, existing metric: com.codahale.metrics.Counter");
    }

    @Test
    public void testInaccessibleConstructor() throws Exception {
        Constructor<?> constructor = MetricRegistries.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testMetricsPrefixedBy() {
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
    public void testNullPrefixMetricsPrefixedBy() {
        assertThatThrownBy(() -> MetricRegistries.metricsPrefixedBy(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testMetricsMatching() {
        MetricFilter palantirFilter = (name, metric) -> name.startsWith("test");

        metrics.counter("test.a");
        metrics.timer("test.b");
        metrics.counter("non.matching");

        SortedMap<String, Metric> metricsMatching = MetricRegistries.metricsMatching(metrics, palantirFilter);
        assertThat(metricsMatching.size()).isEqualTo(2);
        assertThat(metricsMatching.keySet())
                .containsExactly("test.a", "test.b");
        assertThat(metricsMatching.values())
                .containsExactly(metrics.counter("test.a"), metrics.timer("test.b"));
    }

    @Test
    public void testTimestamp() throws Exception {
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(MetricRegistries.nowIsoTimestamp());
    }

    private static void report(MetricRegistry metrics) {
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .build();
        reporter.report();
        reporter.stop();
    }

}
