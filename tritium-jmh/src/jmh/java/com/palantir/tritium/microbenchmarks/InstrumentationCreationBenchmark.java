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

import com.google.common.collect.Streams;
import com.google.common.util.concurrent.Runnables;
import com.palantir.tritium.Tritium;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings({"designforextension", "NullAway"})
public class InstrumentationCreationBenchmark {

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

    private TaggedMetricRegistry registry;
    private Stubs.Iface99 largeStub;
    private Stubs.Iface99 duplicateStub;

    @Setup
    public void before() {
        mode.initialize();
        this.registry = new DefaultTaggedMetricRegistry();
        this.largeStub = (Stubs.Iface99) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                // Apply runnable to avoid benchmarks reusing the proxy class
                new Class<?>[] {Runnable.class, Stubs.Iface99.class},
                (_proxy, _method, _args) -> null);
        this.duplicateStub = (Stubs.Iface99) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                // Apply runnable to avoid benchmarks reusing the proxy class
                Streams.concat(
                                Stream.<Class<?>>of(Runnable.class),
                                IntStream.range(0, 100).<Class<?>>mapToObj(value -> {
                                    try {
                                        return Class.forName(
                                                Stubs.Iface99.class.getName().replace("99", "" + value));
                                    } catch (ClassNotFoundException e) {
                                        throw new IllegalStateException(e);
                                    }
                                }))
                        .toArray(Class<?>[]::new),
                (_proxy, _method, _args) -> null);
    }

    @Benchmark
    public void createInstrumentationLargeInterface() {
        Tritium.instrument(Stubs.Iface99.class, largeStub, registry).iface50method5();
    }

    @Benchmark
    public void createInstrumentationLargeComplexInterface() {
        Tritium.instrument(Stubs.Iface99.class, duplicateStub, registry).iface50method5();
    }

    @Benchmark
    public void createInstrumentationSimpleInterface() {
        Tritium.instrument(Runnable.class, Runnables.doNothing(), registry).run();
    }

    public static void main(String[] _args) throws Exception {
        Options options = new OptionsBuilder()
                .include(InstrumentationCreationBenchmark.class.getName())
                .warmupIterations(0)
                .measurementIterations(1)
                .mode(Mode.SingleShotTime)
                .forks(1)
                .build();
        new Runner(options).run();
    }
}
