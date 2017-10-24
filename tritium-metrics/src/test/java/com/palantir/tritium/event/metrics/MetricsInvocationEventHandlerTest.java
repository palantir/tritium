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

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.api.event.InvocationContext;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.metrics.annotations.MetricGroup;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.tags.TaggedMetric;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

public final class MetricsInvocationEventHandlerTest {

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

    private MetricRegistry metrics = new MetricRegistry();

    @After
    public void after() {
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertDurationsTo(TimeUnit.MICROSECONDS)
                .convertRatesTo(TimeUnit.MICROSECONDS)
                .build();
        reporter.report();
        reporter.stop();
    }

    @Test
    public void testFailure() throws Exception {
        MetricsInvocationEventHandler handler = MetricsInvocationEventHandler.create(metrics, "test");

        InvocationContext context = mock(InvocationContext.class);
        when(context.getMethod()).thenReturn(String.class.getDeclaredMethod("length"));
        assertThat(metrics.getMeters()).doesNotContainKeys("failures");

        handler.onFailure(context, new RuntimeException("unexpected"));

        SortedMap<String, Metric> metricsMatching = MetricRegistries.metricsMatching(
                metrics, MetricRegistries.metricsWithTag("error"));
        assertThat(metricsMatching.get("test[error:java.lang.RuntimeException,method:length]"))
                .isInstanceOf(Timer.class)
                .extracting("count")
                .contains(1L);
    }

    @Test
    public void testOnSuccessNullContext() {
        MetricsInvocationEventHandler handler = MetricsInvocationEventHandler.create(metrics, "test");
        assertThat(metrics.getMeters().get("failures")).isNull();

        handler.onSuccess(null, new Object());

        assertThat(metrics.getMeters().get("failures")).isNull();
    }

    @Test
    public void testOnFailureNullContext() {
        MetricsInvocationEventHandler handler = MetricsInvocationEventHandler.create(metrics, "test");
        assertThat(metrics.getMeters().get("failures")).isNull();

        handler.onFailure(null, new RuntimeException("expected"));

        SortedMap<String, Metric> metricsMatching = MetricRegistries.metricsMatching(
                metrics, MetricRegistries.metricsWithTag("error"));
        assertThat(metricsMatching.get("test[error:java.lang.RuntimeException]"))
                .isInstanceOf(Meter.class)
                .extracting("count")
                .contains(1L);
    }

    @Test
    public void testSystemPropertySupplier_Handler_Enabled() {
        assertThat(MetricsInvocationEventHandler.getEnabledSupplier("test").asBoolean()).isTrue();
    }

    // TODO (davids): remove or migrate to tags?
    @Ignore
    @Test
    public void testMetricGroupAnnotations() throws Exception {
        AnnotatedTestInterface obj = mock(AnnotatedTestInterface.class);
        when(obj.methodA()).thenReturn("ok");

        AnnotatedOtherInterface other = mock(AnnotatedOtherInterface.class);

        String globalPrefix = "com.business.myservice";

        String name = TaggedMetric.toCanonicalName(obj.getClass().getName(), ImmutableMap.of("x1", globalPrefix));
        MetricsInvocationEventHandler handler =
                MetricsInvocationEventHandler.create(metrics, name);

        String name2 = TaggedMetric.toCanonicalName(other.getClass().getName(), ImmutableMap.of("x1", globalPrefix));
        MetricsInvocationEventHandler otherHandler =
                MetricsInvocationEventHandler.create(metrics, name2);

        //AnnotatedTestInterface
        callVoidMethod(handler, obj, "methodA", true);
        callVoidMethod(handler, obj, "methodB", true);
        callVoidMethod(handler, obj, "methodC", true);
        callVoidMethod(handler, obj, "methodD", true);
        callVoidMethod(handler, obj, "methodA", false);

        assertThat(metrics.timer(obj.getClass().getName() + ".ONE").getCount()).isEqualTo(2L);
        assertThat(metrics.timer(obj.getClass().getName() + ".TWO").getCount()).isEqualTo(1L);
        assertThat(metrics.timer(obj.getClass().getName() + ".DEFAULT").getCount()).isEqualTo(1L);
        assertThat(metrics.timer(obj.getClass().getName() + ".ONE.failures").getCount()).isEqualTo(1L);

        //AnnotatedOtherInterface
        callVoidMethod(otherHandler, other, "methodE", true);
        assertThat(metrics.timer(other.getClass().getName() + ".DEFAULT").getCount()).isEqualTo(1L);

        //GlobalPrefix Tests
        assertThat(metrics.timer(globalPrefix + ".DEFAULT").getCount()).isEqualTo(2L);
        assertThat(metrics.timer(globalPrefix + ".ONE").getCount()).isEqualTo(2L);
    }

    private void callVoidMethod(
            MetricsInvocationEventHandler handler, Object obj, String methodName, boolean success) throws Exception {

        InvocationContext context = DefaultInvocationContext.of(obj, obj.getClass().getMethod(methodName), null);
        if (success) {
            handler.onSuccess(context, null);
        } else {
            handler.onFailure(context, new RuntimeException("test failure"));
        }

    }

}
