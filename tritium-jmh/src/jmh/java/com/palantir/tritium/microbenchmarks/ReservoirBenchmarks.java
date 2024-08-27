/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.tritium.metrics.registry.LockFreeExponentiallyDecayingReservoir;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(32)
@State(Scope.Benchmark)
@SuppressWarnings({"designforextension", "NullAway"})
public class ReservoirBenchmarks {

    @Param({"EXPO_DECAY", "LOCK_FREE_EXPO_DECAY", "DW_LOCK_FREE_EXPO_DECAY"})
    private ReservoirType reservoirType;

    public enum ReservoirType {
        EXPO_DECAY() {
            @Override
            Reservoir create() {
                return new ExponentiallyDecayingReservoir();
            }
        },
        LOCK_FREE_EXPO_DECAY() {
            @Override
            @SuppressWarnings("deprecation") // explicitly testing
            Reservoir create() {
                return LockFreeExponentiallyDecayingReservoir.builder().build();
            }
        },

        DW_LOCK_FREE_EXPO_DECAY() {
            @Override
            Reservoir create() {
                return com.codahale.metrics.LockFreeExponentiallyDecayingReservoir.builder()
                        .build();
            }
        };

        abstract Reservoir create();
    }

    private Reservoir reservoir;

    @Setup
    public void before() {
        reservoir = reservoirType.create();
    }

    @Benchmark
    public void updateReservoir() {
        reservoir.update(1L);
    }
}
