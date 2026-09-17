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

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Snapshot;
import com.palantir.tritium.metrics.registry.LockFreeExponentiallyDecayingReservoir;
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
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings({"designforextension", "NullAway"})
public class SnapshotBenchmark {

    @Param({"WEIGHTED_SNAPSHOT", "PRIMITIVE_WEIGHTED_SNAPSHOT"})
    private SnapshotType snapshotType;

    @Param({"100", "1028"})
    private int reservoirSize;

    public enum SnapshotType {
        WEIGHTED_SNAPSHOT {
            @Override
            Reservoir create(int size) {
                return new ExponentiallyDecayingReservoir(size, 0.015);
            }
        },
        PRIMITIVE_WEIGHTED_SNAPSHOT {
            @Override
            @SuppressWarnings("deprecation")
            Reservoir create(int size) {
                return LockFreeExponentiallyDecayingReservoir.builder()
                        .size(size)
                        .alpha(0.015)
                        .build();
            }
        };

        abstract Reservoir create(int size);
    }

    private Reservoir reservoir;

    @Setup
    public void before() {
        reservoir = snapshotType.create(reservoirSize);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < reservoirSize * 2; i++) {
            reservoir.update(rng.nextLong(0, 10_000_000));
        }
    }

    @Benchmark
    public Snapshot getSnapshot() {
        return reservoir.getSnapshot();
    }

    @Benchmark
    public void getSnapshotAndRead(Blackhole bh) {
        Snapshot snapshot = reservoir.getSnapshot();
        bh.consume(snapshot.getMedian());
        bh.consume(snapshot.get75thPercentile());
        bh.consume(snapshot.get95thPercentile());
        bh.consume(snapshot.get99thPercentile());
        bh.consume(snapshot.getMean());
        bh.consume(snapshot.getStdDev());
    }
}
