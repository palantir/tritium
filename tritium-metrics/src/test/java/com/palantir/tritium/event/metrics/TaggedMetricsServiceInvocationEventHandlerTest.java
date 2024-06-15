/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Metric;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.metrics.InstrumentationMetrics.Invocation_Result;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.metrics.test.TestTaggedMetricRegistries;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class TaggedMetricsServiceInvocationEventHandlerTest {

    public static final class TestImplementation {

        @SuppressWarnings("unused") // instrumented
        public String doFoo() {
            return this.getClass().getSimpleName();
        }
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testTaggedServiceMetricsCaptured(TaggedMetricRegistry registry) throws Exception {
        TestImplementation testInterface = new TestImplementation();

        TaggedMetricsServiceInvocationEventHandler handler =
                new TaggedMetricsServiceInvocationEventHandler(registry, "serviceName");

        invokeMethod(handler, testInterface, "doFoo", "bar", /* success= */ true);

        Map<MetricName, Metric> metrics = registry.getMetrics();
        MetricName expectedMetricName = InstrumentationMetrics.of(registry)
                .invocation()
                .serviceName("serviceName")
                .endpoint("doFoo")
                .result(Invocation_Result.SUCCESS)
                .buildMetricName();
        assertThat(metrics).containsKey(expectedMetricName);
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testTaggedServiceMetricsCapturedAsErrors(TaggedMetricRegistry registry) throws Exception {
        TestImplementation testInterface = new TestImplementation();

        TaggedMetricsServiceInvocationEventHandler handler =
                new TaggedMetricsServiceInvocationEventHandler(registry, "serviceName");

        invokeMethod(handler, testInterface, "doFoo", "bar", /* success= */ false);

        Map<MetricName, Metric> metrics = registry.getMetrics();
        MetricName expectedMetricName = InstrumentationMetrics.of(registry)
                .invocation()
                .serviceName("serviceName")
                .endpoint("doFoo")
                .result(Invocation_Result.FAILURE)
                .buildMetricName();
        assertThat(metrics).containsKey(expectedMetricName);
    }

    @SuppressWarnings("SameParameterValue")
    private static void invokeMethod(
            AbstractInvocationEventHandler<?> handler, Object obj, String methodName, Object result, boolean success)
            throws Exception {
        InvocationContext context =
                DefaultInvocationContext.of(obj, obj.getClass().getMethod(methodName), null);
        if (success) {
            handler.onSuccess(context, result);
        } else {
            handler.onFailure(context, new SafeRuntimeException("fail"));
        }
    }
}
