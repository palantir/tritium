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

package com.palantir.tritium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Timer;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.util.SortedMap;
import org.junit.After;
import org.junit.Test;

public class TritiumTest {

    private static final String EXPECTED_METRIC_NAME = "service.response[method:test,service-name:TestInterface]";

    private TestImplementation delegate = new TestImplementation();
    private MetricRegistry metrics = new MetricRegistry();
    private TestInterface instrumentedService = Tritium.instrument(TestInterface.class, delegate, metrics);

    @After
    public void after() {
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics).build();
        reporter.report();
        reporter.stop();
    }

    @Test
    public void testInstrument() {
        assertThat(delegate.invocationCount()).isEqualTo(0);
        assertThat(metrics.getTimers().get(Runnable.class.getName())).isNull();

        instrumentedService.test();
        assertThat(delegate.invocationCount()).isEqualTo(1);

        SortedMap<String, Timer> timers = metrics.getTimers();
        assertThat(timers).containsOnlyKeys(EXPECTED_METRIC_NAME)
                .hasSize(1)
                .extracting(EXPECTED_METRIC_NAME)
                .extracting("count")
                .contains(1L);

        instrumentedService.test();

        assertThat(timers.get(EXPECTED_METRIC_NAME).getCount()).isEqualTo(delegate.invocationCount());
        assertThat(timers.get(EXPECTED_METRIC_NAME).getSnapshot().getMax()).isGreaterThan(-1L);

        Slf4jReporter.forRegistry(metrics).withLoggingLevel(Slf4jReporter.LoggingLevel.INFO).build().report();
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

        assertThat(metrics.getMeters()).doesNotContainKeys(
                MetricRegistry.name(methodMetricName, "failures"));

        assertThat(metrics.getMeters()).doesNotContainKeys(
                MetricRegistry.name(methodMetricName, "failures", "java.lang.OutOfMemoryError"));

        assertThatThrownBy(() -> instrumentedService.throwsOutOfMemoryError())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("Testing OOM");

        assertThat(MetricRegistries.metricsMatching(metrics,
                MetricRegistries.metricsWithTag("error"))).containsOnlyKeys(
                "service.response"
                        + "["
                        + "error:java.lang.OutOfMemoryError,"
                        + "method:throwsOutOfMemoryError,"
                        + "service-name:TestInterface"
                        + "]");

        assertThat(metrics.getMeters((name, metric) -> name.contains("failures"))).isEmpty();

        SortedMap<String, Timer> errors = metrics.getTimers(MetricRegistries.metricsWithTag("error"));
        assertThat(errors).containsOnlyKeys("service.response"
                + "["
                + "error:java.lang.OutOfMemoryError,"
                + "method:throwsOutOfMemoryError,"
                + "service-name:TestInterface"
                + "]");

        assertThat(errors.get("service.response"
                + "["
                + "error:java.lang.OutOfMemoryError,"
                + "method:throwsOutOfMemoryError,"
                + "service-name:TestInterface"
                + "]").getCount())
                .isEqualTo(1);
    }
}
