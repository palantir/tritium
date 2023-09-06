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

package com.palantir.tritium.ids;

import com.fasterxml.uuid.Generators;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Threads(Threads.MAX)
@Fork(1)
@SuppressWarnings({"checkstyle:hideutilityclassconstructor", "VisibilityModifier", "DesignForExtension"})
public class UuidBenchmark {

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecureRandom sha1secureRandom = UniqueIds.createSecureRandom();
    private final ThreadLocal<Random> threadLocalSecureRandom = ThreadLocal.withInitial(UniqueIds::createSecureRandom);

    @Benchmark
    @Threads(1)
    public UUID jdkRandomUuidOne() {
        return UUID.randomUUID();
    }

    @Benchmark
    @Threads(Threads.MAX)
    public UUID jdkRandomUuidMax() {
        return UUID.randomUUID();
    }

    @Benchmark
    @Threads(1)
    public UUID randomUuidV4One() {
        return UniqueIds.randomUuidV4();
    }

    @Benchmark
    @Threads(Threads.MAX)
    public UUID randomUuidV4Max() {
        return UniqueIds.randomUuidV4();
    }

    @Benchmark
    @Threads(Threads.MAX)
    public UUID secureRandomMax() {
        return UniqueIds.randomUuidV4(sha1secureRandom);
    }

    @Benchmark
    @Threads(Threads.MAX)
    public UUID threadLocalMax() {
        return UniqueIds.randomUuidV4(threadLocalSecureRandom.get());
    }

    @Benchmark
    @Threads(Threads.MAX)
    public UUID twoLongsMax() {
        return new UUID(secureRandom.nextLong(), secureRandom.nextLong());
    }

    @Benchmark
    @Threads(Threads.MAX)
    public UUID jugSecureRandomMax() {
        return Generators.randomBasedGenerator(sha1secureRandom).generate();
    }

    public static void main(String[] _args) throws Exception {
        new Runner(new OptionsBuilder()
                        .include(UuidBenchmark.class.getSimpleName())
                        .forks(1)
                        .threads(4)
                        .warmupIterations(3)
                        .warmupTime(TimeValue.seconds(3))
                        .measurementIterations(3)
                        .measurementTime(TimeValue.seconds(1))
                        .build())
                .run();
    }
}
