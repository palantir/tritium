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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Counter;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public final class TaggedMetricRegistryTest {

    private static final MetricName METRIC_1 = MetricName.builder().safeName("name").build();
    private static final MetricName METRIC_2 = MetricName.builder().safeName("name").putSafeTags("key", "val").build();

    private TaggedMetricRegistry registry;

    @Before
    public void before() {
        registry = new DefaultTaggedMetricRegistry();
    }

    interface SuppliedMetricMethod<T extends Metric> {
        T metric(MetricName metricName, Supplier<T> supplier);
    }

    interface MetricMethod<T extends Metric> {
        T metric(MetricName metricName);
    }

    private <T extends Metric> void testNonsuppliedCall(MetricMethod<T> registryMethod) {
        T metric1 = registryMethod.metric(METRIC_1);
        T metric2 = registryMethod.metric(METRIC_2);

        assertThat(metric1).isNotSameAs(metric2);
        assertThat(registryMethod.metric(METRIC_1)).isSameAs(metric1);
        assertThat(registryMethod.metric(METRIC_2)).isSameAs(metric2);
    }

    private <T extends Metric> void testSuppliedCall(SuppliedMetricMethod<T> registryMethod, T mock1, T mock2) {
        Supplier<T> mockSupplier = mock(Supplier.class);
        when(mockSupplier.get()).thenReturn(mock1).thenReturn(mock2);

        assertThat(registryMethod.metric(METRIC_1, mockSupplier)).isSameAs(mock1);
        assertThat(registryMethod.metric(METRIC_2, mockSupplier)).isSameAs(mock2);
        assertThat(registryMethod.metric(METRIC_1, mockSupplier)).isSameAs(mock1); // should be memoized
        assertThat(registryMethod.metric(METRIC_2, mockSupplier)).isSameAs(mock2); // should be memoized

        Mockito.verify(mockSupplier, times(2)).get();
    }

    @Test
    public void testCounter() {
        testNonsuppliedCall(registry::counter);
    }

    @Test
    public void testSuppliedCounter() {
        testSuppliedCall(registry::counter, new Counter(), new Counter());
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
        testNonsuppliedCall(registry::histogram);
    }

    @Test
    public void testSuppliedHistogram() {
        testSuppliedCall(registry::histogram,
                new Histogram(new ExponentiallyDecayingReservoir()),
                new Histogram(new ExponentiallyDecayingReservoir()));
    }

    @Test
    public void testMeter() {
        testNonsuppliedCall(registry::meter);
    }

    @Test
    public void testSuppliedMeter() {
        testSuppliedCall(registry::meter, new Meter(), new Meter());
    }

    @Test
    public void testTimer() {
        testNonsuppliedCall(registry::timer);
    }

    @Test
    public void testSuppliedTimer() {
        testSuppliedCall(registry::timer, new Timer(), new Timer());
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
