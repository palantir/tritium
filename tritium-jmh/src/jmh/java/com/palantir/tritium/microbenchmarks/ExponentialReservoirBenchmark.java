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
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@SuppressWarnings("NullAway")
public class ExponentialReservoirBenchmark {
    @Param({"uniform", "3", "4", "8"})
    private String scale;

    private Reservoir reservoir;

    @Setup
    public final void before() {
        reservoir =
                scale.equals("uniform") ? new UniformReservoir() : new ExponentialReservoir(Integer.parseInt(scale));
        for (int value = 1; value <= 10_000; value++) {
            reservoir.update(value);
        }
    }

    @Benchmark
    public final void update() {
        reservoir.update(1234);
    }

    @Benchmark
    public final Snapshot snapshot() {
        return reservoir.getSnapshot();
    }
}
