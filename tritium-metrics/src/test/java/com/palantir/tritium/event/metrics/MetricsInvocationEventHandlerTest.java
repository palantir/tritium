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
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.metrics.annotations.MetricGroup;
import org.junit.Test;

public class MetricsInvocationEventHandlerTest {

    @MetricGroup("DEFAULT")
    public interface AnnotatedTestInterface {

        @MetricGroup("ONE")
        String methodA();

        @MetricGroup("ONE")
        void methodB();

        @MetricGroup("TWO")
        void methodC();

        //Should match the default
        void methodD();
    }

    @MetricGroup("DEFAULT")
    public interface AnnotatedOtherInterface {
        void methodE();
    }

    @Test
    public void testFailure() throws Exception {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");

        InvocationContext context = mock(InvocationContext.class);
        when(context.getMethod()).thenReturn(String.class.getDeclaredMethod("length"));
        assertThat(metricRegistry.getMeters().get("failures")).isNull();

        handler.onFailure(context, new RuntimeException("unexpected"));

        assertThat(metricRegistry.getMeters().get("failures")).isNotNull();
        assertThat(metricRegistry.getMeters().get("failures").getCount()).isEqualTo(1L);
    }

    @Test
    public void testOnSuccessNullContext() {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");
        assertThat(metricRegistry.getMeters().get("failures")).isNull();

        handler.onSuccess(null, new Object());

        assertThat(metricRegistry.getMeters().get("failures")).isNull();
    }

    @Test
    public void testOnFailureNullContext() {
        MetricRegistry metricRegistry = new MetricRegistry();
        MetricsInvocationEventHandler handler = new MetricsInvocationEventHandler(metricRegistry, "test");
        assertThat(metricRegistry.getMeters().get("failures")).isNull();

        handler.onFailure(null, new RuntimeException("expected"));

        assertThat(metricRegistry.getMeters().get("failures")).isNotNull();
        assertThat(metricRegistry.getMeters().get("failures").getCount()).isEqualTo(1L);
    }

    @Test
    public void testSystemPropertySupplier_Handler_Enabled() {
        assertThat(MetricsInvocationEventHandler.getEnabledSupplier("test").getAsBoolean()).isTrue();
    }

    @Test
    public void testMetricGroupAnnotations() throws Exception {
        AnnotatedTestInterface obj = mock(AnnotatedTestInterface.class);
        when(obj.methodA()).thenReturn("ok");

        AnnotatedOtherInterface other = mock(AnnotatedOtherInterface.class);

        MetricRegistry metricRegistry = new MetricRegistry();
        String globalPrefix = "com.business.myservice";

        MetricsInvocationEventHandler handler =
                new MetricsInvocationEventHandler(metricRegistry, obj.getClass(), globalPrefix);

        MetricsInvocationEventHandler otherHandler =
                new MetricsInvocationEventHandler(metricRegistry, other.getClass(), globalPrefix);

        //AnnotatedTestInterface
        callVoidMethod(handler, obj, "methodA", true);
        callVoidMethod(handler, obj, "methodB", true);
        callVoidMethod(handler, obj, "methodC", true);
        callVoidMethod(handler, obj, "methodD", true);
        callVoidMethod(handler, obj, "methodA", false);

        assertThat(metricRegistry.timer(obj.getClass().getName() + ".ONE").getCount()).isEqualTo(2L);
        assertThat(metricRegistry.timer(obj.getClass().getName() + ".TWO").getCount()).isEqualTo(1L);
        assertThat(metricRegistry.timer(obj.getClass().getName() + ".DEFAULT").getCount()).isEqualTo(1L);
        assertThat(metricRegistry.timer(obj.getClass().getName() + ".ONE.failures").getCount()).isEqualTo(1L);

        //AnnotatedOtherInterface
        callVoidMethod(otherHandler, other, "methodE", true);
        assertThat(metricRegistry.timer(other.getClass().getName() + ".DEFAULT").getCount()).isEqualTo(1L);

        //GlobalPrefix Tests
        assertThat(metricRegistry.timer(globalPrefix + ".DEFAULT").getCount()).isEqualTo(2L);
        assertThat(metricRegistry.timer(globalPrefix + ".ONE").getCount()).isEqualTo(2L);
    }

    private static void callVoidMethod(
            MetricsInvocationEventHandler handler, Object obj, String methodName, boolean success) throws Exception {

        InvocationContext context = DefaultInvocationContext.of(obj, obj.getClass().getMethod(methodName), null);
        if (success) {
            handler.onSuccess(context, null);
        } else {
            handler.onFailure(context, new RuntimeException("test failure"));
        }

    }

}
