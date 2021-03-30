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
import com.palantir.tritium.v1.api.event.InvocationContext;
import com.palantir.tritium.v1.core.event.DefaultInvocationContext;
import org.junit.jupiter.api.Test;

@SuppressWarnings({
    "deprecation", // explicitly testing deprecated types
    "NullAway", // implicitly testing null handling
    "UnnecessarilyFullyQualified" // deprecated types
})
final class MetricsInvocationEventHandlerTest {

    @com.palantir.tritium.event.metrics.annotations.MetricGroup("DEFAULT")
    interface AnnotatedTestInterface {

        @com.palantir.tritium.event.metrics.annotations.MetricGroup("ONE")
        String methodA();

        @com.palantir.tritium.event.metrics.annotations.MetricGroup("ONE")
        void methodB();

        @com.palantir.tritium.event.metrics.annotations.MetricGroup("TWO")
        void methodC();

        // Should match the default
        void methodD();
    }

    @com.palantir.tritium.event.metrics.annotations.MetricGroup("DEFAULT")
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

    @Test
    void testMetricGroupAnnotations() throws Exception {
        AnnotatedTestInterface obj = mock(AnnotatedTestInterface.class);
        when(obj.methodA()).thenReturn("ok");

        AnnotatedOtherInterface other = mock(AnnotatedOtherInterface.class);

        MetricRegistry metricRegistry = new MetricRegistry();
        String globalPrefix = "com.business.myservice";

        MetricsInvocationEventHandler handler =
                new MetricsInvocationEventHandler(metricRegistry, obj.getClass(), globalPrefix);

        MetricsInvocationEventHandler otherHandler =
                new MetricsInvocationEventHandler(metricRegistry, other.getClass(), globalPrefix);

        // AnnotatedTestInterface
        callVoidMethod(handler, obj, "methodA", /* success= */ true);
        callVoidMethod(handler, obj, "methodB", /* success= */ true);
        callVoidMethod(handler, obj, "methodC", /* success= */ true);
        callVoidMethod(handler, obj, "methodD", /* success= */ true);
        callVoidMethod(handler, obj, "methodA", /* success= */ false);

        assertThat(metricRegistry.timer(obj.getClass().getName() + ".ONE").getCount())
                .isEqualTo(2L);
        assertThat(metricRegistry.timer(obj.getClass().getName() + ".TWO").getCount())
                .isOne();
        assertThat(metricRegistry.timer(obj.getClass().getName() + ".DEFAULT").getCount())
                .isOne();
        assertThat(metricRegistry
                        .timer(obj.getClass().getName() + ".ONE.failures")
                        .getCount())
                .isOne();

        // AnnotatedOtherInterface
        callVoidMethod(otherHandler, other, "methodE", /* success= */ true);
        assertThat(metricRegistry.timer(other.getClass().getName() + ".DEFAULT").getCount())
                .isOne();

        // GlobalPrefix Tests
        assertThat(metricRegistry.timer(globalPrefix + ".DEFAULT").getCount()).isEqualTo(2L);
        assertThat(metricRegistry.timer(globalPrefix + ".ONE").getCount()).isEqualTo(2L);
    }

    private static void callVoidMethod(
            MetricsInvocationEventHandler handler, Object obj, String methodName, boolean success) throws Exception {
        InvocationContext context =
                DefaultInvocationContext.of(obj, obj.getClass().getMethod(methodName), null);
        if (success) {
            handler.onSuccess(context, null);
        } else {
            handler.onFailure(context, new RuntimeException("test failure"));
        }
    }
}
