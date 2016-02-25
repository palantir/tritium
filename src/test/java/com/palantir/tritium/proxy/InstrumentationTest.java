/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.proxy;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Slf4jReporter.LoggingLevel;
import com.codahale.metrics.Timer;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.SortedMap;
import org.junit.Test;
import org.slf4j.Logger;

public final class InstrumentationTest {

    private static final String EXPECTED_METRIC_NAME = TestInterface.class.getName() + ".test";

    // Exceed the HotSpot JIT thresholds
    private static final int INVOCATION_ITERATIONS = 1_500_000;


    @Test
    public void testEmptyHandlers() {
        TestImplementation delegate = new TestImplementation();
        TestInterface instrumented = Instrumentation.wrap(TestInterface.class, delegate, Collections.emptyList());
        assertThat(instrumented, is(delegate));
        assertThat(Proxy.isProxyClass(instrumented.getClass()), equalTo(false));
    }

    @Test
    public void testBuilder() {
        TestImplementation delegate = new TestImplementation();

        MetricRegistry metricRegistry = MetricRegistries.createWithHdrHistogramReservoirs();

        TestInterface instrumentedService = Instrumentation.builder(TestInterface.class, delegate)
                .withMetrics(metricRegistry)
                .withPerformanceTraceLogging()
                .build();

        assertThat(delegate.invocationCount(), equalTo(0));
        assertThat(metricRegistry.getTimers().get(Runnable.class.getName()), nullValue());

        instrumentedService.test();
        assertThat(delegate.invocationCount(), equalTo(1));

        SortedMap<String, Timer> timers = metricRegistry.getTimers();
        assertThat(timers.keySet(), hasSize(1));
        assertThat(timers.keySet(), equalTo(ImmutableSet.of(EXPECTED_METRIC_NAME)));
        assertThat(timers.get(EXPECTED_METRIC_NAME), notNullValue());
        assertThat(timers.get(EXPECTED_METRIC_NAME).getCount(), equalTo(1L));

        executeManyTimes(instrumentedService, INVOCATION_ITERATIONS);
        Slf4jReporter.forRegistry(metricRegistry).withLoggingLevel(LoggingLevel.INFO).build().report();

        assertThat(Long.valueOf(timers.get(EXPECTED_METRIC_NAME).getCount()).intValue(),
                equalTo(delegate.invocationCount()));
        assertTrue(timers.get(EXPECTED_METRIC_NAME).getSnapshot().getMax() >= 0L);

        Slf4jReporter.forRegistry(metricRegistry).withLoggingLevel(LoggingLevel.INFO).build().report();
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
                    .withLogging(logger, level, nanos -> false)
                    .build();
            executeManyTimes(instrumentedService, 100);
        }
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
        Instrumentation.builder(Runnable.class, new TestImplementation()).withLogging(null, null, nanos -> false);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testInaccessibleConstructor() throws ReflectiveOperationException {
        Constructor<Instrumentation> constructor = Instrumentation.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            constructor.newInstance();
        } catch (InvocationTargetException expected) {
            throw Throwables.propagate(expected.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            //noinspection ThrowFromFinallyBlock
            constructor.setAccessible(false);
        }
    }

}
