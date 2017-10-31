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

package com.palantir.tritium.event.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import com.palantir.tritium.api.event.InvocationContext;
import com.palantir.tritium.metrics.MetricName;
import com.palantir.tritium.metrics.TaggedMetricRegistry;
import com.palantir.tritium.metrics.Tags;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Test;

public final class MetricsInvocationEventHandlerTest {

    private TaggedMetricRegistry metrics = new TaggedMetricRegistry();
    private MetricsInvocationEventHandler handler = MetricsInvocationEventHandler.create(metrics,
            () -> MetricName.builder().name("test").build());

    @After
    public void after() {
        System.out.println(metrics.getMetrics());
    }

    @Test
    public void testFailure() throws Exception {
        InvocationContext context = mock(InvocationContext.class);
        when(context.getMethod()).thenReturn(String.class.getDeclaredMethod("length"));
        Map<MetricName, Metric> errors = metrics.getMetrics().entrySet().stream()
                .filter(e -> e.getValue() instanceof Meter)
                .filter(e -> Tags.ERROR.key().equals(e.getKey().name()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(errors).isEmpty();

        handler.onFailure(context, new RuntimeException("unexpected"));

        MetricName metricName = MetricName.builder()
                .name("test")
                .putTags(Tags.METHOD.key(), "length")
                .putTags(Tags.ERROR.key(), "java.lang.RuntimeException")
                .build();

        MetricName exceptionSpecificMeter = errorMetricName("java.lang.RuntimeException");

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

    @Test
    public void testOnSuccessNullContext() {
        handler.onSuccess(null, new Object());

        assertThat(getMeters()).isEmpty();
    }

    @Test
    public void testOnFailureNullContext() {
        handler.onFailure(null, new RuntimeException("expected"));

        MetricName exceptionSpecificMeter = errorMetricName("java.lang.RuntimeException");

        assertThat(metrics.getMetrics()).containsOnlyKeys(exceptionSpecificMeter);

        assertThat(metrics.getMetrics().get(exceptionSpecificMeter))
                .isInstanceOf(Meter.class)
                .extracting("count")
                .contains(1L);
    }

    @Test
    public void testSystemPropertySupplier_Handler_Enabled() {
        assertThat(MetricsInvocationEventHandler.getEnabledSupplier("test").asBoolean()).isTrue();
    }

    static MetricName errorMetricName(String errorName) {
        return MetricName.builder()
                .name("test")
                .putTags(Tags.ERROR.key(), errorName)
                .build();
    }

    Map<MetricName, Meter> getMeters() {
        return metrics.getMetrics().entrySet().stream()
                .filter(e -> e.getValue() instanceof Meter)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (Meter) e.getValue()));
    }

}
