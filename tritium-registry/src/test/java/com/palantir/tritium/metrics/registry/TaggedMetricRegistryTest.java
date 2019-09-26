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
import com.palantir.tritium.registry.test.TestTaggedMetricRegistries;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

final class TaggedMetricRegistryTest {

    private static final MetricName METRIC_1 = MetricName.builder().safeName("name").build();
    private static final MetricName METRIC_2 = MetricName.builder().safeName("name").putSafeTags("key", "val").build();

    interface SuppliedMetricMethod<T extends Metric> {
        T metric(MetricName metricName, Supplier<T> supplier);
    }

    interface MetricMethod<T extends Metric> {
        T metric(MetricName metricName);
    }

    private static <T extends Metric> void testNonsuppliedCall(MetricMethod<T> registryMethod) {
        T metric1 = registryMethod.metric(METRIC_1);
        T metric2 = registryMethod.metric(METRIC_2);

        assertThat(metric1).isNotSameAs(metric2);
        assertThat(registryMethod.metric(METRIC_1)).isSameAs(metric1);
        assertThat(registryMethod.metric(METRIC_2)).isSameAs(metric2);
    }

    private static <T extends Metric> void testSuppliedCall(SuppliedMetricMethod<T> registryMethod, T mock1, T mock2) {
        @SuppressWarnings("unchecked")
        Supplier<T> mockSupplier = mock(Supplier.class);
        when(mockSupplier.get()).thenReturn(mock1).thenReturn(mock2);

        assertThat(registryMethod.metric(METRIC_1, mockSupplier)).isSameAs(mock1);
        assertThat(registryMethod.metric(METRIC_2, mockSupplier)).isSameAs(mock2);
        assertThat(registryMethod.metric(METRIC_1, mockSupplier))
                .describedAs("should be memoized")
                .isSameAs(mock1);
        assertThat(registryMethod.metric(METRIC_2, mockSupplier))
                .describedAs("should be memoized")
                .isSameAs(mock2);

        Mockito.verify(mockSupplier, times(2)).get();
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testCounter(TaggedMetricRegistry registry) {
        testNonsuppliedCall(registry::counter);
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testSuppliedCounter(TaggedMetricRegistry registry) {
        testSuppliedCall(registry::counter, new Counter(), new Counter());
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testGauge(TaggedMetricRegistry registry) {
        Gauge<Integer> gauge1 = registry.gauge(METRIC_1, () -> 1);
        Gauge<Integer> gauge2 = registry.gauge(METRIC_2, () -> 2);

        assertThat(gauge1.getValue()).isOne();
        assertThat(gauge2.getValue()).isEqualTo(2);

        assertThat(gauge1).isNotSameAs(gauge2);
        assertThat(registry.gauge(METRIC_1, () -> 3)).isSameAs(gauge1);
        assertThat(registry.gauge(METRIC_2, () -> 4)).isSameAs(gauge2);
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testHistogram(TaggedMetricRegistry registry) {
        testNonsuppliedCall(registry::histogram);
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testSuppliedHistogram(TaggedMetricRegistry registry) {
        testSuppliedCall(registry::histogram,
                new Histogram(new ExponentiallyDecayingReservoir()),
                new Histogram(new ExponentiallyDecayingReservoir()));
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testMeter(TaggedMetricRegistry registry) {
        testNonsuppliedCall(registry::meter);
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testSuppliedMeter(TaggedMetricRegistry registry) {
        testSuppliedCall(registry::meter, new Meter(), new Meter());
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testTimer(TaggedMetricRegistry registry) {
        testNonsuppliedCall(registry::timer);
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testSuppliedTimer(TaggedMetricRegistry registry) {
        testSuppliedCall(registry::timer, new Timer(), new Timer());
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testExistingMetric(TaggedMetricRegistry registry) {
        registry.counter(METRIC_1);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> registry.timer(METRIC_1))
                .withMessageStartingWith("Metric name already used for different metric type: ")
                .withMessageContaining("metricName=name")
                .withMessageContaining("existingMetricType=Counter")
                .withMessageContaining("newMetricType=Timer")
                .withMessageContaining("safeTags={}");
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testRemoveMetric(TaggedMetricRegistry registry) {
        Gauge<Integer> gauge = () -> 42;
        Gauge<Integer> registeredGauge = registry.gauge(METRIC_1, gauge);
        assertThat(registeredGauge).isSameAs(gauge);

        Optional<Metric> removedGauge = registry.remove(METRIC_1);
        assertThat(removedGauge).isPresent().get().isSameAs(gauge);
        assertThat(registry.remove(METRIC_1).isPresent()).isFalse();
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRY_SUPPLIERS)
    void testAddMetricRegistry(Supplier<TaggedMetricRegistry> registrySupplier) {
        TaggedMetricRegistry registry = registrySupplier.get();
        String name = "name";
        String tagKey = "tagKey";
        String tagValue = "tagValue";
        TaggedMetricRegistry child = registrySupplier.get();
        Meter meter = child.meter(MetricName.builder().safeName(name).build());
        registry.addMetrics(tagKey, tagValue, child);
        assertMetric(registry, name, tagKey, tagValue, meter);
        registry.removeMetrics(tagKey, tagValue);
        assertThat(registry.getMetrics()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRY_SUPPLIERS)
    void testReplaceMetricRegistry(Supplier<TaggedMetricRegistry> registrySupplier) {
        TaggedMetricRegistry registry = registrySupplier.get();
        String name = "name";
        String tagKey = "tagKey";
        String tagValue = "tagValue";

        TaggedMetricRegistry firstChild = registrySupplier.get();
        Meter firstMeter = firstChild.meter(MetricName.builder().safeName(name).build());
        registry.addMetrics(tagKey, tagValue, firstChild);

        assertMetric(registry, name, tagKey, tagValue, firstMeter);

        TaggedMetricRegistry secondChild = registrySupplier.get();
        Meter secondMeter = secondChild.meter(MetricName.builder().safeName(name).build());

        registry.addMetrics(tagKey, tagValue, secondChild);
        assertMetric(registry, name, tagKey, tagValue, secondMeter);

        assertThat(registry.removeMetrics(tagKey, tagValue, firstChild)).isFalse();

        assertMetric(registry, name, tagKey, tagValue, secondMeter);

        assertThat(registry.removeMetrics(tagKey, tagValue, secondChild)).isTrue();
        assertThat(registry.getMetrics()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testGetMetrics(TaggedMetricRegistry registry) {
        MetricName metricName = MetricName.builder()
                .safeName("counter1")
                .putSafeTags("tagA", Long.toString(1))
                .putSafeTags("tagB", Long.toString(2))
                .build();
        Counter counter = registry.counter(metricName);
        counter.inc();
        Metric metric = registry.getMetrics().get(metricName);
        assertThat(metric)
                .isInstanceOf(Counter.class)
                .isSameAs(counter)
                .isSameAs(registry.counter(
                        MetricName.builder()
                                .safeName("counter1")
                                .putSafeTags("tagB", "2")
                                .putSafeTags("tagA", Long.toString(1))
                                .build()))
                .isSameAs(registry.getMetrics().get(MetricName.builder()
                        .safeName("counter1")
                        .putSafeTags("tagA", Long.toString(1))
                        .putSafeTags("tagB", Integer.toString(2))
                        .build()));
        assertThat(counter.getCount()).isOne();
    }

    private static void assertMetric(
            TaggedMetricRegistry registry,
            String name,
            String tagKey,
            String tagValue,
            Meter meter) {
        assertThat(registry.getMetrics())
                .containsEntry(MetricName.builder().safeName(name).putSafeTags(tagKey, tagValue).build(), meter);
    }
}
