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

import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.UniformReservoir;
import com.palantir.tritium.metrics.registry.ExponentialReservoir;
import com.palantir.tritium.metrics.registry.LockFreeExponentiallyDecayingReservoir;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.ThreadParams;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(8)
@SuppressWarnings("NullAway")
public class ExponentialReservoirBenchmark {
    @Param({"uniform", "decaying", "-4", "0", "3", "4", "8"})
    private String scale;

    private Reservoir reservoir;

    @Setup
    public final void before() {
        reservoir = switch (scale) {
            case "uniform" -> new UniformReservoir();
            case "decaying" -> LockFreeExponentiallyDecayingReservoir.builder().build();
            default -> new ExponentialReservoir(Integer.parseInt(scale));
        };
        for (long value : latencyValues(0)) {
            reservoir.update(value);
        }
    }

    // Synthetic request durations in nanoseconds: median 5 ms, with a long tail capped at 60 seconds.
    private static long[] latencyValues(long seed) {
        Random random = new Random(seed);
        long[] values = new long[4096];
        for (int index = 0; index < values.length; index++) {
            values[index] =
                    Math.max(1, (long) Math.min(60_000_000_000.0, 5_000_000 * Math.exp(random.nextGaussian() * 1.2)));
        }
        return values;
    }

    @State(Scope.Thread)
    @SuppressWarnings("NullAway")
    public static class Observations {
        private long[] values;
        private int index;

        @Setup
        public final void before(ThreadParams threads) {
            values = latencyValues(threads.getThreadIndex());
        }

        final long next() {
            long value = values[index];
            index = (index + 1) % values.length;
            return value;
        }
    }

    @Benchmark
    public final void update(Observations observations) {
        reservoir.update(observations.next());
    }

    @Benchmark
    @Threads(1)
    public final Snapshot snapshot() {
        return reservoir.getSnapshot();
    }

    @Benchmark
    @Group("reporting")
    @GroupThreads(7)
    public final void updateWhileReporting(Observations observations) {
        reservoir.update(observations.next());
    }

    // Snapshot continuously to stress contention with updates.
    @Benchmark
    @Group("reporting")
    @GroupThreads(1)
    public final Snapshot snapshotWhileUpdating() {
        return reservoir.getSnapshot();
    }
}
