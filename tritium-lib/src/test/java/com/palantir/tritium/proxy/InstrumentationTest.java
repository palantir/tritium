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
import static org.mockito.Mockito.times;
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
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationFilters;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.event.log.LoggingInvocationEventHandler;
import com.palantir.tritium.event.metrics.MetricsInvocationEventHandler;
import com.palantir.tritium.event.metrics.TaggedMetricsServiceInvocationEventHandler;
import com.palantir.tritium.event.metrics.annotations.MetricGroup;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.test.LessSpecificReturn;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import com.palantir.tritium.tracing.TracingInvocationEventHandler;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"NullAway", "WeakerAccess"}) // mock injection
public abstract class InstrumentationTest {

    @MetricGroup("DEFAULT")
    public interface AnnotatedInterface {
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

    abstract boolean useByteBuddy();

    @BeforeEach
    void before() {
        System.setProperty("instrument.dynamic-proxy", Boolean.toString(!useByteBuddy()));
        InstrumentationProperties.reload();
    }

    @AfterEach
    void after() {
        try (ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics).build()) {
            if (!metrics.getMetrics().isEmpty()) {
                System.out.println("Untagged Metrics:");
                reporter.report();
            }
            Tagged.report(reporter, taggedMetricRegistry);
        }
        System.clearProperty("instrument.dynamic-proxy");
        InstrumentationProperties.reload();
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
        timer.stop();
        System.out.printf("%s took %s for %d iterations%n", getClass(), timer, invocations);

        timer.reset().start();
        instrumentedService.test();
        System.out.printf("Single shot took %s%n", timer);
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
        InvocationEventHandler<?> handler = TracingInvocationEventHandler.create("test");
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

    @Test
    void testInstrumentAnonymousClass() {
        Supplier<String> anonymousSupplier = new Supplier<String>() {
            @Override
            public String get() {
                return "AnonymousSupplier.get";
            }

            @Override
            public String toString() {
                return "AnonymousSupplier";
            }
        };
        Supplier<?> result = Instrumentation.builder(Supplier.class, anonymousSupplier)
                .withPerformanceTraceLogging()
                .build();
        assertThat(result.get()).isEqualTo("AnonymousSupplier.get");
        assertThat(result.toString()).isEqualTo("AnonymousSupplier");
    }

    @Test
    void testInstrumentPrivateClass() {
        Supplier<?> result = Instrumentation.builder(Supplier.class, new PrivateSupplier())
                .withPerformanceTraceLogging()
                .build();
        assertThat(result.get()).isEqualTo("PrivateSupplier.get");
        assertThat(result.toString()).isEqualTo("PrivateSupplier");
    }

    private static final class PrivateSupplier implements Supplier<String> {

        @Override
        public String get() {
            return "PrivateSupplier.get";
        }

        @Override
        public String toString() {
            return "PrivateSupplier";
        }
    }

    @Test
    void testInstrumentMultipleInterfacesProvideSameMethod(
            @Mock InvocationEventHandler<InvocationContext> mockHandler) {
        when(mockHandler.isEnabled()).thenReturn(true);
        when(mockHandler.preInvocation(any(), any(), any()))
                .thenAnswer((Answer<InvocationContext>) invocation -> DefaultInvocationContext.of(
                        invocation.getMock(), invocation.getMethod(), invocation.getArguments()));
        ParentInterface result = Instrumentation.builder(ParentInterface.class, new DefaultParent())
                .withHandler(mockHandler)
                .build();
        assertThat(result.getValue()).isEqualTo(3);
        assertThat(result.getString()).isEqualTo("string");
        verify(mockHandler, times(2)).isEnabled();
        verify(mockHandler, times(2)).preInvocation(any(), any(), any());
        verify(mockHandler, times(2)).onSuccess(any(), any());
        verifyNoMoreInteractions(mockHandler);
        assertThat(result)
                .isInstanceOf(FirstInterface.class)
                .isInstanceOf(SecondInterface.class)
                .isInstanceOf(ParentInterface.class);
    }

    @Test
    void testAdditionalInterfaces(@Mock InvocationEventHandler<InvocationContext> mockHandler) {
        when(mockHandler.isEnabled()).thenReturn(true);
        when(mockHandler.preInvocation(any(), any(), any()))
                .thenAnswer((Answer<InvocationContext>) invocation -> DefaultInvocationContext.of(
                        invocation.getMock(), invocation.getMethod(), invocation.getArguments()));
        FirstInterface result = Instrumentation.builder(FirstInterface.class, new DefaultParent())
                .withHandler(mockHandler)
                .build();
        assertThat(result)
                .isInstanceOf(FirstInterface.class)
                .isInstanceOf(SecondInterface.class)
                .isInstanceOf(ParentInterface.class);

        assertThat(result.getValue()).isEqualTo(3);
        assertThat(result).isInstanceOfSatisfying(ParentInterface.class,
                parentInterface -> assertThat(parentInterface.getString()).isEqualTo("string"));
        verify(mockHandler, times(2)).isEnabled();
        verify(mockHandler, times(2)).preInvocation(any(), any(), any());
        verify(mockHandler, times(2)).onSuccess(any(), any());
        verifyNoMoreInteractions(mockHandler);
    }

    public interface FirstInterface {

        int getValue();

    }

    public interface SecondInterface {

        int getValue();

    }

    public interface ParentInterface extends FirstInterface, SecondInterface {

        String getString();
    }

    public static final class DefaultParent implements ParentInterface {

        @Override
        public int getValue() {
            return 3;
        }

        @Override
        public String getString() {
            return "string";
        }
    }

    @Test
    void testStackDepth() {
        StackTraceSupplier stackTraceSupplier = () -> cleanStackTrace(new Exception().getStackTrace());
        StackTraceSupplier instrumentedStackSupplier =
                Instrumentation.builder(StackTraceSupplier.class, stackTraceSupplier)
                        .withPerformanceTraceLogging()
                        .build();
        StackTraceElement[] rawStack = stackTraceSupplier.get();
        StackTraceElement[] instrumentedStack = instrumentedStackSupplier.get();
        // The value isn't particularly important, this test exists to force us to acknowledge changes in
        // stack trace length due to Tritium instrumentation. It's not uncommon to have >10 Tritium proxies
        // in a single trace, so increases in frames can make debugging more difficult.
        int instrumentedStackSize = useByteBuddy() ? 1 : 6;
        assertThat(instrumentedStack).hasSize(rawStack.length + instrumentedStackSize);
    }

    @Test
    void testStackDepth_notFunctionOfHandlers() {
        StackTraceSupplier stackTraceSupplier = () -> cleanStackTrace(new Exception().getStackTrace());
        StackTraceSupplier singleHandlerInstrumentedStackSupplier =
                Instrumentation.builder(StackTraceSupplier.class, stackTraceSupplier)
                        // Use enabled event handlers to avoid bugs from optimizations on disabled handlers
                        .withHandler(new TaggedMetricsServiceInvocationEventHandler(
                                new DefaultTaggedMetricRegistry(), "test0"))
                        .build();
        StackTraceSupplier multipleHandlerInstrumentedStackSupplier =
                Instrumentation.builder(StackTraceSupplier.class, stackTraceSupplier)
                        .withHandler(new TaggedMetricsServiceInvocationEventHandler(
                                new DefaultTaggedMetricRegistry(), "test1"))
                        .withHandler(new MetricsInvocationEventHandler(new MetricRegistry(), "test2"))
                        .build();
        StackTraceElement[] singleHandlerInstrumentedStackTrace = singleHandlerInstrumentedStackSupplier.get();
        StackTraceElement[] multipleHandlerInstrumentedStack = multipleHandlerInstrumentedStackSupplier.get();
        // Tritium frames should not scale with the number of registered InvocationEventHandler instances.
        assertThat(singleHandlerInstrumentedStackTrace).hasSameSizeAs(multipleHandlerInstrumentedStack);
    }

    public interface StackTraceSupplier {
        StackTraceElement[] get();
    }

    /** Provides a subsection of the input stack trace relevant to this test. */
    private static StackTraceElement[] cleanStackTrace(StackTraceElement[] stackTrace) {
        String className = InstrumentationTest.class.getName();
        int lastTestFrame = -1;
        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            if (className.equals(element.getClassName())) {
                lastTestFrame = i;
            }
        }
        assertThat(lastTestFrame).isNotNegative();
        return Arrays.copyOf(stackTrace, lastTestFrame + 1);
    }

    @SuppressWarnings("SameParameterValue")
    private static InstrumentationFilter methodNameFilter(String methodName) {
        return (instance, method, args) -> method.getName().equals(methodName);
    }

    @Test
    void testMultipleTritiumWrappersResultInSameClass() {
        Runnable raw = () -> {};
        Runnable instrumented = Instrumentation.builder(Runnable.class, raw)
                .withPerformanceTraceLogging()
                .build();
        assertThat(instrumented.getClass()).isNotEqualTo(raw.getClass());
        Runnable doubleInstrumented = Instrumentation.builder(Runnable.class, instrumented)
                .withPerformanceTraceLogging()
                .build();
        assertThat(doubleInstrumented.getClass())
                .describedAs("The instrumentation class should be reused")
                .isEqualTo(instrumented.getClass());
    }

    @Test
    void testHigherParentSpecificity() {
        Parent instrumentedService = Instrumentation.builder(Parent.class, new Impl())
                .withPerformanceTraceLogging()
                .build();
        assertThat(instrumentedService.run()).isEqualTo(2);
        assertThat(instrumentedService.specificity()).isEqualTo("more specific");
    }

    public interface Parent extends LessSpecificReturn {
        int run();
    }

    private static final class Impl implements Parent {

        @Override
        public String specificity() {
            return "more specific";
        }

        @Override
        public int run() {
            return 2;
        }
    }
}
