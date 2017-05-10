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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import org.mpierce.metrics.reservoir.hdrhistogram.HdrHistogramReservoir;

public class MetricRegistriesTest {

    private MetricRegistry metrics = MetricRegistries.createWithHdrHistogramReservoirs();

    @After
    public void after() throws Exception {
        report(metrics);
    }

    @Test
    public void defaultMetrics() throws Exception {
        assertThat(metrics.getGauges().size()).isEqualTo(3);
        assertThat(metrics.getGauges()).containsKey(MetricRegistries.RESERVOIR_TYPE_METRIC_NAME);
        assertThat(metrics.getGauges().get(MetricRegistries.RESERVOIR_TYPE_METRIC_NAME).getValue()).isEqualTo(
                HdrHistogramReservoir.class.getName());
        assertThat(metrics.getGauges().keySet()).containsExactly(
                MetricRegistries.RESERVOIR_TYPE_METRIC_NAME,
                "com.palantir.tritium.metrics.snapshot.begin",
                "com.palantir.tritium.metrics.snapshot.now"
        );
        report(metrics);
    }

    @Test
    public void testHdrHistogram() throws Exception {
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
    public void testRegisterCache() throws InterruptedException {
        LoadingCache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(1L)
                .recordStats()
                .build(new CacheLoader<Integer, String>() {
                    @Override
                    public String load(Integer key) throws Exception {
                        return String.valueOf(key);
                    }
                });
        MetricRegistries.registerCache(metrics, cache, "test");

        assertThat(metrics.getGauges(metricsPrefixedBy("test")).keySet()).containsExactly(
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

        assertThat(cache.getUnchecked(42)).isEqualTo("42");

        assertThat(metrics.getGauges().get("test.cache.request.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test.cache.hit.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.hit.ratio").getValue()).isEqualTo(0.0d);
        assertThat(metrics.getGauges().get("test.cache.miss.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test.cache.miss.ratio").getValue()).isEqualTo(1.0d);
        assertThat(metrics.getGauges().get("test.cache.eviction.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.load.average.millis").getValue()).isInstanceOf(Double.class);
        assertThat(metrics.getGauges().get("test.cache.load.failure.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.load.success.count").getValue()).isEqualTo(1L);

        assertThat(cache.getUnchecked(42)).isEqualTo("42");
        Thread.sleep(700); // let stats snapshot cache expire

        assertThat(metrics.getGauges().get("test.cache.request.count").getValue()).isEqualTo(2L);
        assertThat(metrics.getGauges().get("test.cache.hit.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test.cache.miss.count").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test.cache.eviction.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.load.average.millis").getValue()).isInstanceOf(Double.class);
        assertThat(metrics.getGauges().get("test.cache.load.failure.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.load.success.count").getValue()).isEqualTo(1L);

        cache.getUnchecked(1);
        Thread.sleep(700); // let stats snapshot cache expire

        assertThat(metrics.getGauges().get("test.cache.eviction.count").getValue()).isEqualTo(1L);
    }

    @Test
    public void testNoStats() throws Exception {
        LoadingCache<Integer, String> cache = CacheBuilder.newBuilder()
                .maximumSize(1L)
                .build(new CacheLoader<Integer, String>() {
                    @Override
                    public String load(Integer key) throws Exception {
                        return String.valueOf(key);
                    }
                });

        MetricRegistries.registerCache(metrics, cache, "test");

        assertThat(metrics.getGauges(metricsPrefixedBy("test")).keySet()).containsExactly(
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

        assertThat(cache.getUnchecked(42)).isEqualTo("42");

        assertThat(metrics.getGauges().get("test.cache.request.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.hit.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.hit.ratio").getValue()).isEqualTo(Double.NaN);
        assertThat(metrics.getGauges().get("test.cache.miss.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.miss.ratio").getValue()).isEqualTo(Double.NaN);
        assertThat(metrics.getGauges().get("test.cache.estimated.size").getValue()).isEqualTo(1L);
        assertThat(metrics.getGauges().get("test.cache.eviction.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.load.average.millis").getValue()).isEqualTo(0.0d);
        assertThat(metrics.getGauges().get("test.cache.load.failure.count").getValue()).isEqualTo(0L);
        assertThat(metrics.getGauges().get("test.cache.load.success.count").getValue()).isEqualTo(0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetOrAddDuplicate() {
        MetricRegistry metricRegistry = spy(new MetricRegistry());
        Metric mockMetric = mock(Metric.class);
        when(metricRegistry.register("test", mockMetric)).thenReturn(mockMetric);
        when(metricRegistry.register("test", mockMetric)).thenThrow(new IllegalArgumentException());

        assertThat(MetricRegistries.getOrAdd(metricRegistry, "test",
                new HistogramMetricBuilder(Reservoirs.hdrHistogramReservoirSupplier())))
                .isEqualTo(mockMetric);

        assertThat(MetricRegistries.getOrAdd(metricRegistry, "test",
                new HistogramMetricBuilder(Reservoirs.hdrHistogramReservoirSupplier())))
                .isEqualTo(mockMetric);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidReregistration() {
        MetricRegistries.registerSafe(metrics, "test", metrics.counter("counter"));
        MetricRegistries.registerSafe(metrics, "test", metrics.histogram("histogram"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidGetOrAdd() {
        MetricRegistries.getOrAdd(metrics, "histogram", new HistogramMetricBuilder(
                Reservoirs.hdrHistogramReservoirSupplier()));
        MetricRegistries.getOrAdd(metrics, "histogram", new TimerMetricBuilder(
                Reservoirs.hdrHistogramReservoirSupplier()));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInaccessibleConstructor() {
        Constructor<?> constructor = null;
        try {
            constructor = MetricRegistries.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        } catch (InvocationTargetException expected) {
            throw Throwables.propagate(expected.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (constructor != null) {
                constructor.setAccessible(false);
            }
        }
    }

    private static MetricFilter metricsPrefixedBy(final String prefix) {
        return new MetricFilter() {
            @Override
            public boolean matches(String name, Metric metric) {
                return name.startsWith(prefix);
            }
        };
    }

    private static void report(MetricRegistry metrics) {
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .build();
        reporter.report();
        reporter.stop();
    }

}
