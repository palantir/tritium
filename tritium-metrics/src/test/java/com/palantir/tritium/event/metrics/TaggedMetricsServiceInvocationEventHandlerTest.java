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

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.metrics.InstrumentationMetrics.Invocation_Result;
import com.palantir.tritium.metrics.registry.AbstractTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.metrics.test.TestTaggedMetricRegistries;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
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

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testOverloadsShareTimer(TaggedMetricRegistry registry) throws Exception {
        Overloads instance = new Overloads();
        Method noArgs = Overloads.class.getMethod("doFoo");
        Method intArg = Overloads.class.getMethod("doFoo", int.class);
        Method stringArg = Overloads.class.getMethod("doFoo", String.class);
        assertThat(noArgs.hashCode()).isEqualTo(intArg.hashCode()).isEqualTo(stringArg.hashCode());

        TaggedMetricsServiceInvocationEventHandler handler =
                new TaggedMetricsServiceInvocationEventHandler(registry, "serviceName");
        for (int i = 0; i < 2; i++) {
            for (Method method : List.of(noArgs, intArg, stringArg)) {
                handler.onSuccess(DefaultInvocationContext.of(instance, method, null), null);
            }
        }

        Timer timer = InstrumentationMetrics.of(registry)
                .invocation()
                .serviceName("serviceName")
                .endpoint("doFoo")
                .result(Invocation_Result.SUCCESS)
                .build();
        assertThat(timer.getCount()).isEqualTo(6);
    }

    @Test
    void testInvocationDoesNotWaitForTimerCreationOfCollidingMethod() throws Exception {
        CollidingMethods instance = new CollidingMethods();
        Method first = CollidingMethods.class.getMethod("callaaaa");
        Method second = CollidingMethods.class.getMethod("callaabB");
        Method third = CollidingMethods.class.getMethod("callbBaa");
        assertThat(first.hashCode()).isEqualTo(second.hashCode()).isEqualTo(third.hashCode());

        BlockingTimerRegistry registry = new BlockingTimerRegistry();
        TaggedMetricsServiceInvocationEventHandler handler =
                new TaggedMetricsServiceInvocationEventHandler(registry, "serviceName");
        handler.onSuccess(DefaultInvocationContext.of(instance, first, null), null);
        handler.onSuccess(DefaultInvocationContext.of(instance, second, null), null);

        registry.block = true;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Holds the timer cache bin lock shared by all three methods until released.
            Future<?> creation =
                    executor.submit(() -> handler.onSuccess(DefaultInvocationContext.of(instance, third, null), null));
            assertThat(registry.creating.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> lookup =
                    executor.submit(() -> handler.onSuccess(DefaultInvocationContext.of(instance, second, null), null));
            assertThat(lookup).succeedsWithin(Duration.ofSeconds(10));

            registry.release.countDown();
            assertThat(creation).succeedsWithin(Duration.ofSeconds(10));
        } finally {
            registry.release.countDown();
            executor.shutdownNow();
        }

        assertThat(registry.getMetrics().values())
                .filteredOn(Timer.class::isInstance)
                .extracting(metric -> ((Timer) metric).getCount())
                .containsExactlyInAnyOrder(1L, 2L, 1L);
    }

    public static final class Overloads {
        @SuppressWarnings("unused") // instrumented
        public void doFoo() {}

        @SuppressWarnings("unused") // instrumented
        public void doFoo(int _value) {}

        @SuppressWarnings("unused") // instrumented
        public void doFoo(String _value) {}
    }

    /** Methods whose names have equal hash codes ("aa" and "bB" collide), so their {@link Method} hash codes match. */
    public static final class CollidingMethods {
        @SuppressWarnings("unused") // instrumented
        public void callaaaa() {}

        @SuppressWarnings("unused") // instrumented
        public void callaabB() {}

        @SuppressWarnings("unused") // instrumented
        public void callbBaa() {}
    }

    private static final class BlockingTimerRegistry extends AbstractTaggedMetricRegistry {
        private final CountDownLatch creating = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean block;

        BlockingTimerRegistry() {
            super(ExponentiallyDecayingReservoir::new);
        }

        @Override
        protected Supplier<Timer> timerSupplier() {
            return () -> {
                if (block) {
                    creating.countDown();
                    Uninterruptibles.awaitUninterruptibly(release);
                }
                return new Timer(createReservoir());
            };
        }
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
