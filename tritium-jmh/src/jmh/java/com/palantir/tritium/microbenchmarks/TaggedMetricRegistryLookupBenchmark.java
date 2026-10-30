/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Timer;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Random;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Measures lookups of already registered metrics in a registry large enough that many keys share a hash bin with
 * other keys.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@SuppressWarnings({"designforextension", "NullAway"})
public class TaggedMetricRegistryLookupBenchmark {

    private static final int REGISTRY_SIZE = 4096;

    @State(Scope.Benchmark)
    public static class RegistryState {
        /** Number of distinct metrics the benchmark threads look up, out of {@link #REGISTRY_SIZE}. */
        @Param({"64", "4096"})
        private int lookupKeys;

        private TaggedMetricRegistry registry;
        private MetricName[] names;

        @Setup
        public void before() {
            registry = new DefaultTaggedMetricRegistry();
            for (int i = 0; i < REGISTRY_SIZE; i++) {
                registry.timer(name(i));
            }
            Random random = new Random(42);
            names = new MetricName[lookupKeys];
            for (int i = 0; i < lookupKeys; i++) {
                // Look up with equal but distinct instances, as callers usually build a new name per lookup.
                names[i] = name(random.nextInt(REGISTRY_SIZE));
            }
        }

        private static MetricName name(int index) {
            return MetricName.builder()
                    .safeName("server.response")
                    .putSafeTags("service-name", "Service" + (index % 64))
                    .putSafeTags("endpoint", "endpoint" + index)
                    .build();
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        private int[] order;
        private int cursor;

        @Setup
        public void before(RegistryState registryState) {
            Random random = ThreadLocalRandom.current();
            order = new int[1024];
            for (int i = 0; i < order.length; i++) {
                order[i] = random.nextInt(registryState.names.length);
            }
        }

        int next() {
            int index = order[cursor];
            cursor = (cursor + 1) & (order.length - 1);
            return index;
        }
    }

    @Benchmark
    @Threads(1)
    public Timer lookup_1thread(RegistryState registryState, ThreadState threadState) {
        return lookup(registryState, threadState);
    }

    @Benchmark
    @Threads(8)
    public Timer lookup_8threads(RegistryState registryState, ThreadState threadState) {
        return lookup(registryState, threadState);
    }

    @Benchmark
    @Threads(32)
    public Timer lookup_32threads(RegistryState registryState, ThreadState threadState) {
        return lookup(registryState, threadState);
    }

    private static Timer lookup(RegistryState registryState, ThreadState threadState) {
        return registryState.registry.timer(registryState.names[threadState.next()]);
    }

    public static void main(String[] _args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(TaggedMetricRegistryLookupBenchmark.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
