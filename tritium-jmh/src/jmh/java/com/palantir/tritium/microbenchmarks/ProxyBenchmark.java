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

package com.palantir.tritium.microbenchmarks;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tracing.Tracer;
import com.palantir.tritium.Tritium;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.log.LoggingInvocationEventHandler;
import com.palantir.tritium.event.log.LoggingLevel;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.proxy.Instrumentation;
import com.palantir.tritium.tracing.TracingInvocationEventHandler;
import java.util.concurrent.TimeUnit;
import java.util.function.LongPredicate;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.slf4j.impl.TestLogs;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings({"designforextension", "NullAway"})
public class ProxyBenchmark {
    static {
        System.setProperty("org.slf4j.simpleLogger.log.performance", LoggingLevel.TRACE.name());
        TestLogs.setLevel("performance", LoggingLevel.TRACE.name());
        TestLogs.logTo("/dev/null");
    }

    private static final String DEFAULT_LOG_LEVEL = "org.slf4j.simpleLogger.defaultLogLevel";

    @Param({"BYTE_BUDDY", "DYNAMIC_PROXY"})
    private InstrumentationMode mode;

    @SuppressWarnings("unused")
    public enum InstrumentationMode {
        BYTE_BUDDY,
        DYNAMIC_PROXY;

        void initialize() {
            System.setProperty("instrument.dynamic-proxy", Boolean.toString(this.equals(DYNAMIC_PROXY)));
            InstrumentationProperties.reload();
        }
    }

    @Nullable
    private String previousLogLevel;

    private Service raw;
    private Service instrumentedWithoutHandlers;
    private Service instrumentedWithEnabledNopHandler;
    private Service instrumentedWithPerformanceLogging;
    private Service instrumentedWithMetrics;
    private Service instrumentedWithTaggedMetrics;
    private Service instrumentedWithEverything;
    private Service instrumentedWithTracing;
    private Service instrumentedWithTracingNested;
    private Service instrumentedDefaultUntagged;
    private Service instrumentedDefaultTagged;
    private Runnable instrumentedFailing;
    private ManyArguments instrumentedManyArguments;

    @Setup
    public void before(Blackhole blackhole) {
        mode.initialize();
        previousLogLevel = System.setProperty(DEFAULT_LOG_LEVEL, "WARN");

        raw = new TestService();

        Class<Service> serviceInterface = Service.class;
        instrumentedWithoutHandlers =
                Instrumentation.builder(serviceInterface, raw).build();

        instrumentedWithEnabledNopHandler = Instrumentation.builder(serviceInterface, raw)
                .withHandler(new BlackholeInvocationEventHandler(blackhole))
                .build();

        instrumentedWithPerformanceLogging = Instrumentation.builder(serviceInterface, raw)
                // similar to .withPerformanceTraceLogging() but always log
                .withLogging(
                        Instrumentation.getPerformanceLoggerForInterface(serviceInterface),
                        LoggingLevel.TRACE,
                        (LongPredicate) LoggingInvocationEventHandler.LOG_ALL_DURATIONS)
                .build();

        instrumentedWithMetrics = Instrumentation.builder(serviceInterface, raw)
                .withMetrics(MetricRegistries.createWithHdrHistogramReservoirs())
                .build();

        instrumentedWithTaggedMetrics = Instrumentation.builder(serviceInterface, raw)
                .withTaggedMetrics(new DefaultTaggedMetricRegistry(), serviceInterface.getName())
                .build();

        instrumentedWithTracing = Instrumentation.builder(serviceInterface, raw)
                .withHandler(TracingInvocationEventHandler.create(serviceInterface.getName()))
                .build();

        // Simulates call stacks with many traced services
        instrumentedWithTracingNested = Instrumentation.builder(serviceInterface, raw)
                .withHandlers(IntStream.range(0, 10)
                        .mapToObj(index -> TracingInvocationEventHandler.create(serviceInterface.getName() + index))
                        .collect(ImmutableList.toImmutableList()))
                .build();

        instrumentedWithEverything = Instrumentation.builder(serviceInterface, raw)
                .withMetrics(MetricRegistries.createWithHdrHistogramReservoirs())
                // similar to .withPerformanceTraceLogging() but always log
                .withLogging(
                        Instrumentation.getPerformanceLoggerForInterface(serviceInterface),
                        LoggingLevel.TRACE,
                        (LongPredicate) LoggingInvocationEventHandler.LOG_ALL_DURATIONS)
                .withHandler(TracingInvocationEventHandler.create(serviceInterface.getName()))
                .build();

        instrumentedDefaultUntagged = Tritium.instrument(serviceInterface, raw, new MetricRegistry());

        instrumentedDefaultTagged = Tritium.instrument(serviceInterface, raw, new DefaultTaggedMetricRegistry());

        // pre-compute the exception to avoid measuring exception creation, only the difference from instrumentation.
        UnsupportedOperationException failure = new UnsupportedOperationException();
        instrumentedFailing = Instrumentation.builder(Runnable.class, () -> {
                    throw failure;
                })
                .withPerformanceTraceLogging()
                .build();

        instrumentedManyArguments = Instrumentation.builder(ManyArguments.class, ManyArguments.Implementation.INSTANCE)
                .withPerformanceTraceLogging()
                .build();

        // Prevent DCE from tracing
        Tracer.subscribe("jmh", blackhole::consume);
    }

    @TearDown
    public void after() throws Exception {
        Tracer.unsubscribe("jmh");
        if (previousLogLevel == null) {
            System.clearProperty(DEFAULT_LOG_LEVEL);
        } else {
            System.setProperty(DEFAULT_LOG_LEVEL, previousLogLevel);
        }
    }

    @Benchmark
    public String raw() {
        return raw.echo("test");
    }

    // @Benchmark
    public String instrumentedWithoutHandlers() {
        return instrumentedWithoutHandlers.echo("test");
    }

    @Benchmark
    public String instrumentedWithEnabledNopHandler() {
        return instrumentedWithEnabledNopHandler.echo("test");
    }

    @Benchmark
    public String instrumentedWithPerformanceLogging() {
        return instrumentedWithPerformanceLogging.echo("test");
    }

    @Benchmark
    public String instrumentedWithMetrics() {
        return instrumentedWithMetrics.echo("test");
    }

    @Benchmark
    public String instrumentedWithTaggedMetrics() {
        return instrumentedWithTaggedMetrics.echo("test");
    }

    @Benchmark
    public String instrumentedWithTracing() {
        return instrumentedWithTracing.echo("test");
    }

    @Benchmark
    public String instrumentedWithTracingNested() {
        return instrumentedWithTracingNested.echo("test");
    }

    @Benchmark
    public String instrumentedWithEverything() {
        return instrumentedWithEverything.echo("test");
    }

    @Benchmark
    public String instrumentedDefaultUntagged() {
        return instrumentedDefaultUntagged.echo("test");
    }

    @Benchmark
    public String instrumentedDefaultTagged() {
        return instrumentedDefaultTagged.echo("test");
    }

    @Benchmark
    public Throwable instrumentedFailing() {
        try {
            instrumentedFailing.run();
        } catch (RuntimeException e) {
            return e;
        }
        throw new SafeIllegalStateException("Expected to return");
    }

    @Benchmark
    public void instrumentedManyArguments() {
        instrumentedManyArguments.apply(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,
                28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53,
                54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79,
                80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99);
    }

    public interface Service {
        String echo(String input);
    }

    private static final class TestService implements Service {
        @Override
        public String echo(String input) {
            return input;
        }
    }

    public static void main(String[] _args) throws Exception {
        Options options = new OptionsBuilder()
                .include(ProxyBenchmark.class.getSimpleName())
                .warmupTime(TimeValue.seconds(1))
                .warmupIterations(5)
                .measurementTime(TimeValue.seconds(3))
                .measurementIterations(5)
                .forks(1)
                .build();
        new Runner(options).run();
    }
}
