/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.time;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(4)
@State(Scope.Benchmark)
@SuppressWarnings("checkstyle:hideutilityclassconstructor")
public class TimestampBenchmark {
    private static final Clock minusFour = Clock.offset(Clock.systemUTC(), Duration.ofHours(-4));

    @Benchmark
    public static OffsetDateTime offsetDateTimeNowZone() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    @Benchmark
    public static OffsetDateTime offsetDateTimeNowClock() {
        return OffsetDateTime.now(minusFour);
    }

    @Benchmark
    public static OffsetDateTime utcTimestampNow() {
        return UtcTimestamp.now();
    }

    public static void main(String[] _args) throws Exception {
        new Runner(new OptionsBuilder()
                        .include(TimestampBenchmark.class.getSimpleName())
                        .addProfiler(GCProfiler.class)
                        .build())
                .run();
    }
}
