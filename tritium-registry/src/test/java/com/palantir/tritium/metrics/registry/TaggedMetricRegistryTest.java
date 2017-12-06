/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.metrics.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public final class TaggedMetricRegistryTest {

    private static final MetricName METRIC_1 = MetricName.builder().safeName("name").build();
    private static final MetricName METRIC_2 = MetricName.builder().safeName("name").putSafeTags("key", "val").build();

    private TaggedMetricRegistry registry;

    @Before
    public void before() {
        registry = new DefaultTaggedMetricRegistry();
    }

    @Test
    public void testCounter() {
        Counter counter1 = registry.counter(METRIC_1);
        Counter counter2 = registry.counter(METRIC_2);

        // ensure new counters are created
        assertThat(counter1.getCount()).isEqualTo(0);
        assertThat(counter2.getCount()).isEqualTo(0);

        // ensure they're not the same and can be retrieved using the same name
        assertThat(counter1).isNotSameAs(counter2);
        assertThat(registry.counter(METRIC_1)).isSameAs(counter1);
        assertThat(registry.counter(METRIC_2)).isSameAs(counter2);
    }

    @Test
    public void testGauge() {
        Gauge gauge1 = registry.gauge(METRIC_1, () -> 1);
        Gauge gauge2 = registry.gauge(METRIC_2, () -> 2);

        assertThat(gauge1.getValue()).isEqualTo(1);
        assertThat(gauge2.getValue()).isEqualTo(2);

        assertThat(gauge1).isNotSameAs(gauge2);
        assertThat(registry.gauge(METRIC_1, () -> 3)).isSameAs(gauge1);
        assertThat(registry.gauge(METRIC_2, () -> 4)).isSameAs(gauge2);
    }

    @Test
    public void testHistogram() {
        Histogram histogram1 = registry.histogram(METRIC_1);
        Histogram histogram2 = registry.histogram(METRIC_2);

        assertThat(histogram1.getCount()).isEqualTo(0);
        assertThat(histogram2.getCount()).isEqualTo(0);

        assertThat(histogram1).isNotSameAs(histogram2);
        assertThat(registry.histogram(METRIC_1)).isSameAs(histogram1);
        assertThat(registry.histogram(METRIC_2)).isSameAs(histogram2);
    }

    @Test
    public void testMeter() {
        Meter meter1 = registry.meter(METRIC_1);
        Meter meter2 = registry.meter(METRIC_2);

        assertThat(meter1.getCount()).isEqualTo(0);
        assertThat(meter2.getCount()).isEqualTo(0);

        assertThat(meter1).isNotSameAs(meter2);
        assertThat(registry.meter(METRIC_1)).isSameAs(meter1);
        assertThat(registry.meter(METRIC_2)).isSameAs(meter2);
    }

    @Test
    public void testTimer() {
        Timer timer1 = registry.timer(METRIC_1);
        Timer timer2 = registry.timer(METRIC_2);

        assertThat(timer1.getCount()).isEqualTo(0);
        assertThat(timer2.getCount()).isEqualTo(0);

        assertThat(timer1).isNotSameAs(timer2);
        assertThat(registry.timer(METRIC_1)).isSameAs(timer1);
        assertThat(registry.timer(METRIC_2)).isSameAs(timer2);
    }

    @Test
    public void testExistingMetric() {
        registry.counter(METRIC_1);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> registry.timer(METRIC_1))
                .withMessage("'name' already used for a metric of type 'Counter' but wanted type 'Timer'. tags: {}");
    }

    @Test
    public void testRemoveMetric() {
        Gauge<Integer> gauge = () -> 42;
        Gauge registeredGauge = registry.gauge(METRIC_1, gauge);
        assertThat(registeredGauge).isSameAs(gauge);

        Optional<Metric> removedGauge = registry.remove(METRIC_1);
        assertThat(removedGauge.isPresent()).isTrue();
        assertThat(removedGauge.get()).isSameAs(gauge);

        assertThat(registry.remove(METRIC_1).isPresent()).isFalse();
    }
}
