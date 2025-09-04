/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Clock;
import com.codahale.metrics.MovingAverages;
import com.codahale.metrics.SlidingTimeWindowMovingAverages;
import com.palantir.tritium.metrics.registry.OptimizedSlidingTimeWindowMovingAverages;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 20, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings({"designforextension", "NullAway"})
public class MovingAverageBenchmarks {

    @Param({"SlidingTimeWindowMovingAverages", "OptimizedSlidingTimeWindowMovingAverages"})
    private MovingAveragesType type;

    @Param({"10", "1000"})
    private int recordings;

    public enum MovingAveragesType {
        SlidingTimeWindowMovingAverages() {
            @Override
            MovingAverages create(Clock clock) {
                return new SlidingTimeWindowMovingAverages(clock);
            }
        },
        OptimizedSlidingTimeWindowMovingAverages() {
            @Override
            MovingAverages create(Clock clock) {
                return new OptimizedSlidingTimeWindowMovingAverages(clock);
            }
        };

        abstract MovingAverages create(Clock clock);
    }

    private MovingAverages movingAverages;

    @Setup
    public void before() {
        ManualClock clock = new ManualClock();
        movingAverages = type.create(clock);
        for (int second = 0; second < 60; second++) {
            for (int i = 0; i < recordings; i++) {
                movingAverages.update(i);
            }
            clock.addSeconds(second);
        }
    }

    @Benchmark
    public double getM1Rate() {
        return movingAverages.getM1Rate();
    }

    static final class ManualClock extends Clock {
        private long ticksInNanos;

        public synchronized void addSeconds(int seconds) {
            ticksInNanos += TimeUnit.NANOSECONDS.convert(seconds, TimeUnit.SECONDS);
        }

        @Override
        public synchronized long getTick() {
            return ticksInNanos;
        }

        @Override
        public synchronized long getTime() {
            return TimeUnit.NANOSECONDS.toMillis(ticksInNanos);
        }
    }
}
