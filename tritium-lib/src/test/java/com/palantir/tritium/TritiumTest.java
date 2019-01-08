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

package com.palantir.tritium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.util.Map;
import java.util.SortedMap;
import org.junit.After;
import org.junit.Test;

public class TritiumTest {

    private static final String EXPECTED_METRIC_NAME = TestInterface.class.getName() + ".test";

    private final TestImplementation delegate = new TestImplementation();
    private final MetricRegistry metricRegistry = MetricRegistries.createWithHdrHistogramReservoirs();
    private final TaggedMetricRegistry taggedMetricRegistry = new DefaultTaggedMetricRegistry();
    private final TestInterface instrumentedService = Tritium.instrument(TestInterface.class, delegate, metricRegistry);
    private final TestInterface taggedInstrumentedService =
            Tritium.instrument(TestInterface.class, delegate, taggedMetricRegistry);
    private static final MetricName EXPECTED_TAGGED_METRIC_NAME = MetricName.builder()
            .safeName("com.palantir.tritium.test.TestInterface")
            .putSafeTags("service-name", "TestInterface")
            .putSafeTags("endpoint", "test")
            .build();

    @After
    public void after() {
        try (ConsoleReporter reporter = ConsoleReporter.forRegistry(metricRegistry).build()) {
            reporter.report();
            Tagged.report(reporter, taggedMetricRegistry);
        }
    }

    @Test
    public void testInstrument() {
        assertThat(delegate.invocationCount()).isEqualTo(0);
        assertThat(metricRegistry.getTimers().get(Runnable.class.getName())).isNull();

        instrumentedService.test();
        assertThat(delegate.invocationCount()).isEqualTo(1);

        SortedMap<String, Timer> timers = metricRegistry.getTimers();
        assertThat(timers.keySet()).hasSize(1);
        assertThat(timers.keySet()).isEqualTo(ImmutableSet.of(EXPECTED_METRIC_NAME));
        assertThat(timers.get(EXPECTED_METRIC_NAME)).isNotNull();
        assertThat(timers.get(EXPECTED_METRIC_NAME).getCount()).isEqualTo(1);

        instrumentedService.test();

        assertThat(timers.get(EXPECTED_METRIC_NAME).getCount()).isEqualTo(delegate.invocationCount());
        assertThat(timers.get(EXPECTED_METRIC_NAME).getSnapshot().getMax()).isGreaterThan(-1L);

        Slf4jReporter.forRegistry(metricRegistry).withLoggingLevel(Slf4jReporter.LoggingLevel.INFO).build().report();
    }

    @Test
    public void testInstrumentWithTags() {
        assertThat(delegate.invocationCount()).isEqualTo(0);
        assertThat(taggedMetricRegistry.getMetrics()).isEmpty();

        taggedInstrumentedService.test();
        assertThat(delegate.invocationCount()).isEqualTo(1);

        Map<MetricName, Metric> metrics = taggedMetricRegistry.getMetrics();
        assertThat(metrics.keySet()).containsOnly(EXPECTED_TAGGED_METRIC_NAME);
        Metric actual = metrics.get(EXPECTED_TAGGED_METRIC_NAME);
        assertThat(actual).isInstanceOf(Timer.class);
        Timer timer = (Timer) actual;
        assertThat(timer.getCount()).isEqualTo(1);

        taggedInstrumentedService.test();

        assertThat(metrics.get(EXPECTED_TAGGED_METRIC_NAME)).isSameAs(timer);
        assertThat(timer.getCount()).isEqualTo(2);
        assertThat(timer.getSnapshot().getMax()).isGreaterThan(-1);
    }

    @Test
    public void rethrowOutOfMemoryError() {
        assertThatThrownBy(() -> instrumentedService.throwsOutOfMemoryError())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("Testing OOM");
    }

    @Test
    public void rethrowOutOfMemoryErrorMetrics() {
        String methodMetricName = MetricRegistry.name(TestInterface.class, "throwsOutOfMemoryError");
        assertThat(metricRegistry.meter(
                MetricRegistry.name(methodMetricName, "failures"))
                .getCount())
                .isEqualTo(0);
        assertThat(metricRegistry.meter(
                MetricRegistry.name(methodMetricName, "failures", "java.lang.OutOfMemoryError"))
                .getCount())
                .isEqualTo(0);

        assertThatThrownBy(() -> instrumentedService.throwsOutOfMemoryError())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("Testing OOM");

        assertThat(metricRegistry.meter(
                MetricRegistry.name(methodMetricName, "failures"))
                .getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.meter(
                MetricRegistry.name(methodMetricName, "failures", "java.lang.OutOfMemoryError"))
                .getCount())
                .isEqualTo(1);
    }

    @Test
    public void testToString() {
        assertThat(instrumentedService.toString()).isEqualTo(TestImplementation.class.getName());

        assertThat(Tritium.instrument(TestInterface.class, instrumentedService, metricRegistry).toString())
                .isEqualTo(TestImplementation.class.getName());
    }
}
