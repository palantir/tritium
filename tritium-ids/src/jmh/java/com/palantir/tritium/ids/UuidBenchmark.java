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
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(Threads.MAX)
@SuppressWarnings({"checkstyle:hideutilityclassconstructor", "VisibilityModifier", "DesignForExtension"})
public class UuidBenchmark {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Random random = new Random(secureRandom.nextLong());

    private static final ThreadLocal<Random> threadLocalRandom =
            ThreadLocal.withInitial(() -> new Random(secureRandom.nextLong()));

    @Benchmark
    public UUID jdkRandomUuid() {
        return UUID.randomUUID();
    }

    @Benchmark
    public UUID secureRandom() {
        return UniqueIds.v4RandomUuid(secureRandom);
    }

    @Benchmark
    public UUID random() {
        return UniqueIds.v4RandomUuid(random);
    }

    @Benchmark
    public UUID threadLocal() {
        return UniqueIds.v4RandomUuid(threadLocalRandom.get());
    }

    @Benchmark
    public UUID v4PseudoRandomUuid() {
        return UniqueIds.v4PseudoRandomUuid();
    }

    @Benchmark
    public UUID v4RandomUuid() {
        return UniqueIds.v4RandomUuid();
    }

    @Benchmark
    public UUID jugSecureRandom() {
        return Generators.randomBasedGenerator().generate();
    }

    @Benchmark
    public UUID jugRandom() {
        return Generators.randomBasedGenerator(random).generate();
    }

    @Benchmark
    public UUID jugTimeSecureRandom() {
        return Generators.timeBasedGenerator().generate();
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
