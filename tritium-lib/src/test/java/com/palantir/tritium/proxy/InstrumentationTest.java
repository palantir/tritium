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

package com.palantir.tritium.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Slf4jReporter.LoggingLevel;
import com.codahale.metrics.Timer;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.Tagged;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.InstrumentationFilters;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.event.log.LoggingInvocationEventHandler;
import com.palantir.tritium.event.metrics.annotations.MetricGroup;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import com.palantir.tritium.tracing.TracingInvocationEventHandler;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NullAway") // mock injection
final class InstrumentationTest {

    @MetricGroup("DEFAULT")
    interface AnnotatedInterface {
        @MetricGroup("ONE")
        void method();

        @MetricGroup("ONE")
        void otherMethod();

        void defaultMethod();
    }

    private static final String EXPECTED_METRIC_NAME = TestInterface.class.getName() + ".test";

    // Exceed the HotSpot JIT thresholds
    private static final int INVOCATION_ITERATIONS = 150000;

    private final MetricRegistry metrics = MetricRegistries.createWithHdrHistogramReservoirs();
    private final TaggedMetricRegistry taggedMetricRegistry = new DefaultTaggedMetricRegistry();

    @AfterEach
    void after() {
        try (ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics).build()) {
            if (!metrics.getMetrics().isEmpty()) {
                System.out.println("Untagged Metrics:");
                reporter.report();
            }
            Tagged.report(reporter, taggedMetricRegistry);
        }
    }

    @Test
    void testEmptyHandlers() {
        TestInterface delegate = new TestImplementation();
        TestInterface instrumented = Instrumentation.wrap(TestInterface.class, delegate,
                Collections.emptyList(), InstrumentationFilters.INSTRUMENT_NONE);
        assertThat(instrumented).isEqualTo(delegate);
        assertThat(Proxy.isProxyClass(instrumented.getClass())).isFalse();
    }

    @Test
    void testDeprecatedEmptyHandlers() {
        TestInterface delegate = new TestImplementation();
        @SuppressWarnings("deprecation") // explicitly testing
        TestInterface instrumented = Instrumentation.wrap(TestInterface.class, delegate, Collections.emptyList());
        assertThat(instrumented).isEqualTo(delegate);
        assertThat(Proxy.isProxyClass(instrumented.getClass())).isFalse();
    }

    @Test
    void testBuilder() {
        TestImplementation delegate = new TestImplementation();

        MetricRegistry metricRegistry = MetricRegistries.createWithHdrHistogramReservoirs();

        TestInterface instrumentedService = Instrumentation.builder(TestInterface.class, delegate)
                .withMetrics(metricRegistry)
                .withPerformanceTraceLogging()
                .build();

        assertThat(delegate.invocationCount()).isEqualTo(0);
        assertThat(metricRegistry.getTimers().get(Runnable.class.getName())).isNull();

        instrumentedService.test();
        assertThat(delegate.invocationCount()).isEqualTo(1);

        SortedMap<String, Timer> timers = metricRegistry.getTimers();
        assertThat(timers.keySet()).hasSize(1);
        assertThat(timers.keySet()).isEqualTo(ImmutableSet.of(EXPECTED_METRIC_NAME));
        assertThat(timers.get(EXPECTED_METRIC_NAME)).isNotNull();
        assertThat(timers.get(EXPECTED_METRIC_NAME).getCount()).isEqualTo(1);

        executeManyTimes(instrumentedService, INVOCATION_ITERATIONS);
        Slf4jReporter.forRegistry(metricRegistry).withLoggingLevel(LoggingLevel.INFO).build().report();

        assertThat(timers.get(EXPECTED_METRIC_NAME).getCount()).isEqualTo(delegate.invocationCount());
        assertThat(timers.get(EXPECTED_METRIC_NAME).getSnapshot().getMax() >= 0L).isTrue();

        Slf4jReporter.forRegistry(metricRegistry).withLoggingLevel(LoggingLevel.INFO).build().report();
    }

    @Test
    void testMetricGroupBuilder() {
        AnnotatedInterface delegate = mock(AnnotatedInterface.class);
        String globalPrefix = "com.business.service";

        MetricRegistry metricRegistry = MetricRegistries.createWithHdrHistogramReservoirs();

        AnnotatedInterface instrumentedService = Instrumentation.builder(AnnotatedInterface.class, delegate)
                .withMetrics(metricRegistry, globalPrefix)
                .withPerformanceTraceLogging()
                .build();
        //call
        instrumentedService.method();
        instrumentedService.otherMethod();
        instrumentedService.defaultMethod();

        assertThat(metricRegistry.timer(AnnotatedInterface.class.getName() + ".ONE").getCount()).isEqualTo(2L);
        assertThat(metricRegistry.timer(globalPrefix + ".ONE").getCount()).isEqualTo(2L);
        assertThat(metricRegistry.timer(AnnotatedInterface.class.getName() + ".DEFAULT").getCount()).isEqualTo(1L);
        assertThat(metricRegistry.timer(globalPrefix + ".DEFAULT").getCount()).isEqualTo(1L);
        assertThat(metricRegistry.timer(AnnotatedInterface.class.getName() + ".method").getCount()).isEqualTo(1L);
    }

    private void executeManyTimes(TestInterface instrumentedService, int invocations) {
        Stopwatch timer = Stopwatch.createStarted();
        for (int i = 0; i < invocations; i++) {
            instrumentedService.test();
        }
        System.out.printf("%s took %s for %d iterations %n", getClass(), timer, INVOCATION_ITERATIONS);

        timer.reset().start();
        instrumentedService.test();
        System.out.printf("Single shot took %s", timer);
    }

    @Test
    void testLogLevels() {
        TestImplementation delegate = new TestImplementation();

        Logger logger = Instrumentation.getPerformanceLoggerForInterface(TestInterface.class);
        for (com.palantir.tritium.event.log.LoggingLevel level : com.palantir.tritium.event.log.LoggingLevel.values()) {
            @SuppressWarnings("deprecation") // explicitly testing
            TestInterface instrumentedService = Instrumentation.builder(TestInterface.class, delegate)
                    .withLogging(logger, level, LoggingInvocationEventHandler.NEVER_LOG)
                    .build();
            executeManyTimes(instrumentedService, 100);
        }
    }

    @Test
    void testCheckedExceptions() {
        TestImplementation delegate = new TestImplementation();

        Logger logger = Instrumentation.getPerformanceLoggerForInterface(TestInterface.class);
        for (com.palantir.tritium.event.log.LoggingLevel level : com.palantir.tritium.event.log.LoggingLevel.values()) {
            for (int i = 0; i < 100; i++) {
                @SuppressWarnings("deprecation") // explicitly testing
                TestInterface instrumentedService = Instrumentation.builder(TestInterface.class, delegate)
                        .withLogging(logger, level, LoggingInvocationEventHandler.NEVER_LOG)
                        .build();

                assertThatThrownBy(instrumentedService::throwsCheckedException)
                        .isInstanceOf(TestImplementation.TestException.class)
                        .hasCause(null);
            }
        }
    }

    @Test
    void testThrowables() {
        TestImplementation delegate = new TestImplementation();

        Logger logger = Instrumentation.getPerformanceLoggerForInterface(TestInterface.class);
        for (com.palantir.tritium.event.log.LoggingLevel level : com.palantir.tritium.event.log.LoggingLevel.values()) {
            for (int i = 0; i < 100; i++) {
                @SuppressWarnings("deprecation") // explicitly testing
                TestInterface instrumentedService = Instrumentation.builder(TestInterface.class, delegate)
                        .withLogging(logger, level, LoggingInvocationEventHandler.NEVER_LOG)
                        .build();

                assertThatExceptionOfType(TestImplementation.TestThrowable.class)
                        .isThrownBy(instrumentedService::throwsThrowable)
                        .withMessage("TestThrowable")
                        .withNoCause();
            }
        }
    }

    @Test
    void testFilterSkips(@Mock InvocationEventHandler<InvocationContext> mockHandler) {
        TestInterface delegate = new TestImplementation();
        TestInterface instrumented = Instrumentation.builder(TestInterface.class, delegate)
                .withFilter(methodNameFilter("bulk"))
                .withHandler(mockHandler)
                .build();

        when(mockHandler.isEnabled()).thenReturn(true);

        instrumented.test();
        verify(mockHandler).isEnabled();
        verifyNoMoreInteractions(mockHandler);
    }

    @Test
    void testFilterMatches(@Mock InvocationEventHandler<InvocationContext> mockHandler) throws Exception {
        TestInterface delegate = new TestImplementation();
        TestInterface instrumented = Instrumentation.builder(TestInterface.class, delegate)
                .withFilter(methodNameFilter("bulk"))
                .withHandler(mockHandler)
                .build();

        InvocationContext mockContext = mock(InvocationContext.class);
        when(mockHandler.isEnabled()).thenReturn(true);
        when(mockHandler.preInvocation(any(), any(Method.class), any(Object[].class))).thenReturn(mockContext);

        ImmutableSet<String> testSet = ImmutableSet.of("test");
        instrumented.bulk(testSet);
        verify(mockHandler).isEnabled();
        verify(mockHandler).preInvocation(instrumented,
                TestInterface.class.getDeclaredMethod("bulk", Set.class),
                new Object[] {testSet});
        verify(mockHandler).onSuccess(mockContext, null);
        verifyNoMoreInteractions(mockHandler);
    }

    @Test
    void testNullInterface() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Instrumentation.builder(null, new Object()))
                .withMessage("class");
    }

    @Test
    void testNullDelegate() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Instrumentation.builder(Runnable.class, null))
                .withMessage("delegate");
    }

    @Test
    void testNullMetricRegistry() {
        Instrumentation.Builder<Runnable, TestImplementation> builder = Instrumentation.builder(Runnable.class,
                new TestImplementation());
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> builder.withMetrics(null))
                .withMessage("metricRegistry");
    }

    @Test
    @SuppressWarnings("deprecation") // explicitly testing
    void testNullLogger() {
        Instrumentation.Builder<Runnable, TestImplementation> builder = Instrumentation.builder(
                Runnable.class, new TestImplementation());
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> builder.withLogging(
                        null,
                        com.palantir.tritium.event.log.LoggingLevel.INFO,
                        LoggingInvocationEventHandler.NEVER_LOG))
                .withMessage("logger");
    }

    @Test
    @SuppressWarnings("deprecation") // explicitly testing
    void testNullLogLevel() {
        Instrumentation.Builder<Runnable, TestImplementation> builder = Instrumentation.builder(
                Runnable.class, new TestImplementation());
        Logger logger = LoggerFactory.getLogger(InstrumentationTest.class);
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> builder.withLogging(
                        logger,
                        null,
                        LoggingInvocationEventHandler.NEVER_LOG))
                .withMessage("level");
    }

    @Test
    void testNullFilter() {
        Instrumentation.Builder<Runnable, TestImplementation> builder = Instrumentation.builder(Runnable.class,
                new TestImplementation());
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> builder.withFilter(null))
                .withMessage("instrumentationFilter");
    }

    @Test
    void testTaggedMetrics() {
        TestImplementation delegate = new TestImplementation();
        TestInterface runnable =
                Instrumentation.builder(TestInterface.class, delegate)
                        .withTaggedMetrics(taggedMetricRegistry, "testPrefix")
                        .withMetrics(metrics)
                        .build();
        assertThat(delegate.invocationCount()).isEqualTo(0);
        runnable.test();
        assertThat(delegate.invocationCount()).isEqualTo(1);
        Map<MetricName, Metric> taggedMetrics = taggedMetricRegistry.getMetrics();
        assertThat(taggedMetrics.keySet()).containsExactly(
                MetricName.builder()
                        .safeName("testPrefix")
                        .putSafeTags("service-name", "TestInterface")
                        .putSafeTags("endpoint", "test")
                        .build(),
                // The failures metric is created eagerly
                MetricName.builder()
                        .safeName("failures")
                        .build());

        assertThat(taggedMetrics.values())
                .first().isInstanceOf(Timer.class)
                .extracting(m -> ((Timer) m).getCount()).isEqualTo(1L);
    }

    @Test
    void testInaccessibleConstructor() throws NoSuchMethodException {
        Constructor<Instrumentation> constructor = Instrumentation.class.getDeclaredConstructor();
        assertThat(constructor.isAccessible()).isFalse();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseExactlyInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testEquals_separateInstanceWithSameArgs() {
        TestImplementation delegate = new TestImplementation();
        InvocationEventHandler<InvocationContext> handler = TracingInvocationEventHandler.create("test");
        TestInterface proxy0 = Instrumentation.builder(TestInterface.class, delegate).withHandler(handler).build();
        TestInterface proxy1 = Instrumentation.builder(TestInterface.class, delegate).withHandler(handler).build();
        assertThat(proxy0).isNotEqualTo(proxy1);
    }

    @Test
    void testEquals_sameInstance() {
        TestInterface proxy = Instrumentation.builder(TestInterface.class, new TestImplementation())
                .withPerformanceTraceLogging()
                .build();
        assertThat(proxy).isEqualTo(proxy);
    }

    @Test
    void testHashCode_constant() {
        TestInterface proxy = Instrumentation.builder(TestInterface.class, new TestImplementation())
                .withPerformanceTraceLogging()
                .build();
        assertThat(proxy.hashCode()).isEqualTo(proxy.hashCode());
    }

    @Test
    void testHashCode_notDelegated() {
        TestInterface delegate = new TestImplementation();
        TestInterface proxy = Instrumentation.builder(TestInterface.class, delegate)
                .withPerformanceTraceLogging()
                .build();
        // Small chance of a flake if we get unlucky
        assertThat(proxy.hashCode()).isNotEqualTo(delegate.hashCode());
    }

    @Test
    void testToString_delegateWithoutInstrumentation(@Mock InvocationEventHandler<InvocationContext> mockHandler) {
        TestInterface delegate = new TestImplementation();
        TestInterface instrumented = Instrumentation.builder(TestInterface.class, delegate)
                .withHandler(mockHandler)
                .build();
        assertThat(instrumented).asString().isEqualTo("com.palantir.tritium.test.TestImplementation");
        verifyNoMoreInteractions(mockHandler);
    }

    @SuppressWarnings("SameParameterValue")
    private static InstrumentationFilter methodNameFilter(String methodName) {
        return (instance, method, args) -> method.getName().equals(methodName);
    }
}
