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

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.palantir.tritium.metrics.MetricName;
import com.palantir.tritium.metrics.MetricNames;
import com.palantir.tritium.metrics.TaggedMetricRegistry;
import com.palantir.tritium.metrics.Tags;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Test;

public final class TritiumTest {

    private static final MetricName EXPECTED_METRIC_NAME = MetricName.builder()
            .name(MetricNames.internalServiceResponse())
            .putTags(Tags.METHOD.key(), "test")
            .putTags(Tags.SERVICE.key(), "TestInterface")
            .build();

    private TestImplementation delegate = new TestImplementation();
    private TaggedMetricRegistry metrics = new TaggedMetricRegistry();
    private TestInterface instrumentedService = Tritium.instrument(TestInterface.class, delegate, metrics);

    @After
    public void after() {
        System.out.println(metrics.getMetrics());
    }

    @Test
    public void testInstrument() {
        assertThat(delegate.invocationCount()).isEqualTo(0);
        assertThat(getTimers()).extracting(Runnable.class.getName()).containsNull();

        instrumentedService.test();
        assertThat(delegate.invocationCount()).isEqualTo(1);

        Map<MetricName, Timer> timers = getTimers();
        assertThat(timers).containsOnlyKeys(EXPECTED_METRIC_NAME)
                .hasSize(1);
        assertThat(timers.get(EXPECTED_METRIC_NAME))
                .extracting("count")
                .contains(1L);

        instrumentedService.test();

        assertThat(timers.get(EXPECTED_METRIC_NAME))
                .extracting("count")
                .contains(Long.valueOf(delegate.invocationCount()));
    }

    @Test
    public void rethrowOutOfMemoryError() {
        assertThatThrownBy(() -> instrumentedService.throwsOutOfMemoryError())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("Testing OOM");
    }

    @Test
    public void rethrowOutOfMemoryErrorMetrics() {
        assertThatThrownBy(() -> instrumentedService.throwsOutOfMemoryError())
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("Testing OOM");

        MetricName metricName = MetricName.builder()
                .name(MetricNames.internalServiceResponse())
                .putTags(Tags.METHOD.key(), "throwsOutOfMemoryError")
                .putTags(Tags.SERVICE.key(), "TestInterface")
                .putTags(Tags.ERROR.key(), "java.lang.OutOfMemoryError")
                .build();

        assertThat(getTimers()).containsKeys(metricName);

        MetricName exceptionSpecificMeter = errorMetricName("java.lang.OutOfMemoryError");

        assertThat(getMeters()).containsKeys(exceptionSpecificMeter);

        assertThat(metrics.getMetrics()).containsOnlyKeys(metricName, exceptionSpecificMeter);

        assertThat(metrics.getMetrics().get(metricName))
                .isInstanceOf(Timer.class)
                .extracting("count")
                .contains(1L);

        assertThat(metrics.getMetrics().get(exceptionSpecificMeter))
                .isInstanceOf(Meter.class)
                .extracting("count")
                .contains(1L);
    }

    Map<MetricName, Timer> getTimers() {
        return metrics.getMetrics().entrySet().stream()
                .filter(e -> e.getValue() instanceof Timer)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (Timer) e.getValue()));
    }

    Map<MetricName, Meter> getMeters() {
        return metrics.getMetrics().entrySet().stream()
                .filter(e -> e.getValue() instanceof Meter)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (Meter) e.getValue()));
    }

    static MetricName errorMetricName(String errorName) {
        return MetricName.builder()
                .name(MetricNames.internalServiceResponse())
                .putTags(Tags.SERVICE.key(), "TestInterface")
                .putTags(Tags.ERROR.key(), errorName)
                .build();
    }
}
