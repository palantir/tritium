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

package com.palantir.tritium.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Slf4jReporter.LoggingLevel;
import com.codahale.metrics.Timer;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.InstrumentationFilters;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.event.log.LoggingInvocationEventHandler;
import com.palantir.tritium.event.metrics.annotations.MetricGroup;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import java.util.SortedMap;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;

@RunWith(MockitoJUnitRunner.class)
public class InstrumentationTest {

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

    @Mock
    private InvocationEventHandler<InvocationContext> mockHandler;

    private MetricRegistry metrics = MetricRegistries.createWithHdrHistogramReservoirs();

    @After
    public void after() {
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics).build();
        reporter.report();
        reporter.close();
    }

    @Test
    public void testEmptyHandlers() {
        TestInterface delegate = new TestImplementation();
        TestInterface instrumented = Instrumentation.wrap(TestInterface.class, delegate,
                InstrumentationFilters.INSTRUMENT_NONE, Collections.emptyList());
        assertThat(instrumented).isEqualTo(delegate);
        assertThat(Proxy.isProxyClass(instrumented.getClass())).isFalse();
    }

    @Test
    public void testDeprecatedEmptyHandlers() {
        TestInterface delegate = new TestImplementation();
        @SuppressWarnings("deprecation") // explicitly testing
        TestInterface instrumented = Instrumentation.wrap(TestInterface.class, delegate, Collections.emptyList());
        assertThat(instrumented).isEqualTo(delegate);
        assertThat(Proxy.isProxyClass(instrumented.getClass())).isFalse();
    }

    @Test
    public void testBuilder() {
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
        assertTrue(timers.get(EXPECTED_METRIC_NAME).getSnapshot().getMax() >= 0L);

        Slf4jReporter.forRegistry(metricRegistry).withLoggingLevel(LoggingLevel.INFO).build().report();
    }

    @Test
    public void testMetricGroupBuilder() {
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
    public void testLogLevels() {
        TestImplementation delegate = new TestImplementation();

        Logger logger = Instrumentation.getPerformanceLoggerForInterface(TestInterface.class);
        for (com.palantir.tritium.event.log.LoggingLevel level : com.palantir.tritium.event.log.LoggingLevel.values()) {
            TestInterface instrumentedService = Instrumentation.builder(TestInterface.class, delegate)
                    .withLogging(logger, level, LoggingInvocationEventHandler.NEVER_LOG)
                    .build();
            executeManyTimes(instrumentedService, 100);
        }
    }

    @Test
    public void testCheckedExceptions() {
        TestImplementation delegate = new TestImplementation();

        Logger logger = Instrumentation.getPerformanceLoggerForInterface(TestInterface.class);
        for (com.palantir.tritium.event.log.LoggingLevel level : com.palantir.tritium.event.log.LoggingLevel.values()) {
            for (int i = 0; i < 100; i++) {
                TestInterface instrumentedService = Instrumentation.builder(TestInterface.class, delegate)
                        .withLogging(logger, level, LoggingInvocationEventHandler.NEVER_LOG)
                        .build();
                try {
                    instrumentedService.throwsCheckedException();
                    fail("Expected exception");
                } catch (Exception expected) {
                    assertThat(expected).isInstanceOf(TestImplementation.TestException.class);
                    assertThat(expected.getCause()).isNull();
                }
            }
        }
    }

    @Test
    public void testThrowables() {
        TestImplementation delegate = new TestImplementation();

        Logger logger = Instrumentation.getPerformanceLoggerForInterface(TestInterface.class);
        for (com.palantir.tritium.event.log.LoggingLevel level : com.palantir.tritium.event.log.LoggingLevel.values()) {
            for (int i = 0; i < 100; i++) {
                TestInterface instrumentedService = Instrumentation.builder(TestInterface.class, delegate)
                        .withLogging(logger, level, LoggingInvocationEventHandler.NEVER_LOG)
                        .build();
                try {
                    instrumentedService.throwsThrowable();
                    fail("Expected throwable");
                } catch (Throwable throwable) {
                    assertThat(Throwables.getRootCause(throwable)).isInstanceOf(AssertionError.class);
                    assertThat(throwable).isInstanceOf(AssertionError.class);
                    assertThat(throwable.getCause()).isNull();
                }
            }
        }
    }

    @Test
    public void testFilterSkips() {
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
    public void testFilterMatches() throws Exception {
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

    @Test(expected = NullPointerException.class)
    public void testNullInterface() {
        //noinspection ConstantConditions
        Instrumentation.builder(null, new Object());
    }

    @Test(expected = NullPointerException.class)
    public void testNullDelegate() {
        //noinspection ConstantConditions
        Instrumentation.builder(Runnable.class, null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullMetricRegistry() {
        //noinspection ConstantConditions
        Instrumentation.builder(Runnable.class, new TestImplementation()).withMetrics(null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullLogger() {
        //noinspection ConstantConditions
        Instrumentation.builder(Runnable.class, new TestImplementation())
                .withLogging(null, null, LoggingInvocationEventHandler.NEVER_LOG);
    }

    @Test(expected = NullPointerException.class)
    public void testNullFilter() {
        //noinspection ConstantConditions
        Instrumentation.builder(Runnable.class, new TestImplementation())
                .withFilter(null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInaccessibleConstructor() {
        Constructor<?> constructor = null;
        try {
            constructor = Instrumentation.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        } catch (InvocationTargetException expected) {
            throw Throwables.propagate(expected.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (constructor != null) {
                constructor.setAccessible(false);
            }
        }
    }

    private static InstrumentationFilter methodNameFilter(final String methodName) {
        return (instance, method, args) -> method.getName().equals(methodName);
    }
}
