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

import com.palantir.tritium.Tritium;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.runner.options.TimeValue;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings({"designforextension", "NullAway"})
public class InstrumentationCreationBenchmark {

    @Param("BYTE_BUDDY")
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

    @Setup
    public void before() {
        mode.initialize();
        this.registry = new DefaultTaggedMetricRegistry();
    }

    @SuppressWarnings({"unchecked", "ProxyNonConstantType"})
    @Benchmark
    public void createInstrumentationLargeInterface() throws ClassNotFoundException {
        Class<Stubs.Iface0> iface =
                (Class<Stubs.Iface0>) Class.forName("com.palantir.tritium.microbenchmarks.Stubs$Iface"
                        + ThreadLocalRandom.current().nextInt(100));
        Stubs.Iface0 instrumented = Tritium.instrument(
                iface,
                (Stubs.Iface0) Proxy.newProxyInstance(
                        iface.getClassLoader(),
                        // Apply runnable to avoid benchmarks reusing the proxy class
                        new Class<?>[] {iface},
                        (_proxy, _method, _args) -> {
                            byte[] byteArray = new byte[1024 * 4];
                            ThreadLocalRandom.current().nextBytes(byteArray);
                            if (Arrays.hashCode(byteArray) == 12345) {
                                System.out.println("lucky hashCode!");
                            }
                            if (ThreadLocalRandom.current().nextInt(1000) == 0) {
                                System.gc();
                            }
                            return null;
                        }),
                registry);
        for (int i = 0; i < 10; i++) {
            instrumented.iface0method0();
        }
    }

    public static void main(String[] _args) throws Exception {
        Options options = new OptionsBuilder()
                .include(InstrumentationCreationBenchmark.class.getName() + ".createInstrumentationLargeInterface")
                .warmupIterations(0)
                .threads(2)
                .measurementIterations(1)
                .measurementTime(TimeValue.seconds(10))
                .jvmArgs("-XX:+UseShenandoahGC", "-XX:+ClassUnloadingWithConcurrentMark", "-XX:+UseNUMA")
                .mode(Mode.AverageTime)
                .forks(100)
                .build();
        new Runner(options).run();
    }
}
