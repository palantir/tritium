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

package com.palantir.tritium.event.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codahale.metrics.MetricRegistry;
import com.palantir.tritium.event.InvocationContext;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
final class MetricsInvocationEventHandlerTest {

    interface AnnotatedTestInterface {

        String methodA();

        void methodB();

        void methodC();

        // Should match the default
        void methodD();
    }

    interface AnnotatedOtherInterface {
        void methodE();
    }

    @Test
    @SuppressWarnings("JdkObsolete") // SortedMap is part of Metrics API
    void testFailure() throws Exception {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");

        InvocationContext context = mock(InvocationContext.class);
        when(context.getMethod()).thenReturn(String.class.getDeclaredMethod("length"));
        assertThat(metricRegistry.getMeters()).doesNotContainKey("failures");

        handler.onFailure(context, new RuntimeException("unexpected"));

        assertThat(metricRegistry.getMeters()).containsKey("failures");
        assertThat(metricRegistry.getMeters().get("failures").getCount()).isOne();
    }

    @Test
    void testOnSuccessNullContext() {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");
        assertThat(metricRegistry.getMeters()).doesNotContainKey("failures");

        handler.onSuccess(null, new Object());

        assertThat(metricRegistry.getMeters()).doesNotContainKey("failures");
    }

    @Test
    @SuppressWarnings("JdkObsolete") // SortedMap is part of Metrics API
    void testOnFailureNullContext() {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");
        assertThat(metricRegistry.getMeters()).doesNotContainKey("failures");

        handler.onFailure(null, new RuntimeException("expected"));

        assertThat(metricRegistry.getMeters()).containsKey("failures");
        assertThat(metricRegistry.getMeters().get("failures").getCount()).isOne();
    }

    @Test
    void testSystemPropertySupplier_Handler_Enabled() {
        assertThat(MetricsInvocationEventHandler.getEnabledSupplier("test").getAsBoolean())
                .isTrue();
    }
}
