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

package com.palantir.tritium.microbenchmarks;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LoggingSpanCollector;
import com.github.kristofa.brave.Sampler;
import com.palantir.remoting1.tracing.AsyncSlf4jSpanObserver;
import com.palantir.remoting1.tracing.Tracer;
import com.palantir.tritium.brave.BraveLocalTracingInvocationEventHandler;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.proxy.Instrumentation;
import com.palantir.tritium.tracing.TracingInvocationEventHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings("designforextension")
public class ProxyBenchmark {

    private static final String DEFAULT_LOG_LEVEL = "org.slf4j.simpleLogger.defaultLogLevel";

    private String previousLogLevel;

    private Service raw;
    private Service instrumentedWithoutHandlers;
    private Service instrumentedWithPerformanceLogging;
    private Service instrumentedWithMetrics;
    private Service instrumentedWithEverything;
    private Service instrumentedWithBrave;
    private Service instrumentedWithTracing;

    private ExecutorService executor;

    @Setup
    public void before() {
        previousLogLevel = System.setProperty(DEFAULT_LOG_LEVEL, "WARN");

        raw = new TestService();

        instrumentedWithoutHandlers = Instrumentation.builder(Service.class, raw).build();

        instrumentedWithPerformanceLogging = Instrumentation.builder(Service.class, raw)
                .withPerformanceTraceLogging()
                .build();

        instrumentedWithMetrics = Instrumentation.builder(Service.class, raw)
                .withMetrics(MetricRegistries.createWithHdrHistogramReservoirs())
                .build();

        BraveLocalTracingInvocationEventHandler braveLocalTracingInvocationEventHandler =
                new BraveLocalTracingInvocationEventHandler("testComponent",
                        new Brave.Builder("testService")
                                .clock(() -> System.currentTimeMillis() * 1000)
                                .spanCollector(new LoggingSpanCollector("tracing"))
                                .traceSampler(Sampler.create(0.01f))
                                .build());

        instrumentedWithBrave = Instrumentation.builder(Service.class, raw)
                .withHandler(braveLocalTracingInvocationEventHandler)
                .build();

        TracingInvocationEventHandler tracingInvocationEventHandler = new TracingInvocationEventHandler("jmh");
        executor = Executors.newSingleThreadExecutor();
        Tracer.subscribe("slf4j", AsyncSlf4jSpanObserver.of("test", executor));
        instrumentedWithTracing = Instrumentation.builder(Service.class, raw)
                .withHandler(tracingInvocationEventHandler)
                .build();

        instrumentedWithEverything = Instrumentation.builder(Service.class, raw)
                .withMetrics(MetricRegistries.createWithHdrHistogramReservoirs())
                .withPerformanceTraceLogging()
                .withHandler(braveLocalTracingInvocationEventHandler)
                .withHandler(tracingInvocationEventHandler)
                .build();
    }

    @TearDown
    public void after() throws Exception {
        executor.shutdown();
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

    @Benchmark
    public String instrumentedWithoutHandlers() {
        return instrumentedWithoutHandlers.echo("test");
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
    public String instrumentedWithBrave() {
        return instrumentedWithBrave.echo("test");
    }

    @Benchmark
    public String instrumentedWithTracing() {
        return instrumentedWithTracing.echo("test");
    }

    @Benchmark
    public String instrumentedWithEverything() {
        return instrumentedWithEverything.echo("test");
    }

    public interface Service {
        String echo(String input);
    }

    private static class TestService implements Service {
        @Override
        public String echo(String input) {
            return input;
        }
    }

    public static void main(String[] args) throws Exception {
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
