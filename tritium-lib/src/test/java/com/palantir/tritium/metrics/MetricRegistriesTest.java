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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.palantir.tritium.metrics.MetricRegistries.MetricBuilder;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.Test;

public final class MetricRegistriesTest {

    @Test
    public void testHdrHistogram() throws Exception {
        MetricRegistry metrics = MetricRegistries.createWithHdrHistogramReservoirs();
        assertThat(metrics, notNullValue());

        Histogram histogram = metrics.histogram("histogram");
        histogram.update(42L);
        assertThat(histogram.getCount(), equalTo(1L));
        assertThat(histogram.getSnapshot().getMax(), equalTo(42L));

        metrics.timer("timer").time(() -> {
            Thread.sleep(123L);
            return null;
        });

        assertThat(metrics.getTimers().get("timer").getCount(), equalTo(1L));
    }

    @Test
    public void testRegisterCache() {
        MetricRegistry metrics = MetricRegistries.createWithHdrHistogramReservoirs();
        assertThat(metrics.getGauges().size(), equalTo(0));

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

        assertThat(metrics.getGauges().size(), equalTo(7));

        MetricRegistries.registerCache(metrics, cache, "test");

        assertThat(cache.getUnchecked(42), equalTo("42"));

        assertThat(metrics.getGauges().get("test.request.count").getValue(), equalTo(1L));
        assertThat(metrics.getGauges().get("test.hit.count").getValue(), equalTo(0L));
        assertThat(metrics.getGauges().get("test.hit.ratio").getValue(), equalTo(0.0d));
        assertThat(metrics.getGauges().get("test.miss.count").getValue(), equalTo(1L));
        assertThat(metrics.getGauges().get("test.miss.ratio").getValue(), equalTo(1.0d));
        assertThat(metrics.getGauges().get("test.eviction.count").getValue(), equalTo(0L));

        assertThat(cache.getUnchecked(42), equalTo("42"));

        assertThat(metrics.getGauges().get("test.request.count").getValue(), equalTo(2L));
        assertThat(metrics.getGauges().get("test.hit.count").getValue(), equalTo(1L));
        assertThat(metrics.getGauges().get("test.miss.count").getValue(), equalTo(1L));
        assertThat(metrics.getGauges().get("test.eviction.count").getValue(), equalTo(0L));

        cache.getUnchecked(1);

        assertThat(metrics.getGauges().get("test.eviction.count").getValue(), equalTo(1L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetOrAddDuplicate() {
        MetricRegistry metricRegistry = spy(new MetricRegistry());
        Metric mockMetric = mock(Metric.class);
        when(metricRegistry.register("test", mockMetric)).thenReturn(mockMetric);
        when(metricRegistry.register("test", mockMetric)).thenThrow(new IllegalArgumentException());

        assertThat(MetricRegistries.getOrAdd(metricRegistry, "test", MetricBuilder.HISTOGRAMS), equalTo(mockMetric));

        assertThat(MetricRegistries.getOrAdd(metricRegistry, "test", MetricBuilder.HISTOGRAMS), equalTo(mockMetric));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidReregistration() {
        MetricRegistry metrics = new MetricRegistry();
        MetricRegistries.registerSafe(metrics, "test", metrics.counter("counter"));
        MetricRegistries.registerSafe(metrics, "test", metrics.histogram("histogram"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidGetOrAdd() {
        MetricRegistry metrics = new MetricRegistry();
        MetricRegistries.getOrAdd(metrics, "histogram", MetricRegistries.MetricBuilder.HISTOGRAMS);
        MetricRegistries.getOrAdd(metrics, "histogram", MetricRegistries.MetricBuilder.TIMERS);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInaccessibleConstructor() throws Throwable {
        Constructor<MetricRegistries> constructor = MetricRegistries.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
        } catch (InvocationTargetException expected) {
            throw expected.getCause();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            constructor.setAccessible(false);
        }
    }
}
