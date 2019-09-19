/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.palantir.tritium.event.log.LoggingLevel;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.impl.TestLogs;

public final class JitCompilationTest {

    private static final long COUNT = 5_000_000L;

    static {
        // avoid logging
        TestLogs.setLevel("performance", LoggingLevel.INFO.name());
        TestLogs.logTo("/dev/null");
    }

    public static void main(String[] _args) {
        JitCompilationTest test = new JitCompilationTest();
        test.jitAllSuccess();
        test.jitWithSomeExceptions();
    }

    private final TestImplementation delegate = new TestImplementation() {
        private final List<String> strings = ImmutableList.of("1", "22", "333", "4444", "55555", "666666", "7777777");

        @Override
        public String test() {
            return strings.get(ThreadLocalRandom.current().nextInt(strings.size()));
        }
    };

    private final MetricRegistry metricRegistry = MetricRegistries.createWithHdrHistogramReservoirs();
    private final TestInterface instrumentedService = Tritium.instrument(TestInterface.class, delegate, metricRegistry);

    private void jitAllSuccess() {
        ThreadLocalRandom current = ThreadLocalRandom.current();
        long sum = 0;
        for (int i = 0; i < COUNT; i++) {
            String test = instrumentedService.test();
            assertThat(test).isNotNull();
            sum += test.length();
            sum += current.nextInt(10);
        }

        System.out.printf("Sum: %d %n", sum);
        assertThat(sum).isGreaterThanOrEqualTo(delegate.test().length() * COUNT);
    }

    private void jitWithSomeExceptions() {
        ThreadLocalRandom current = ThreadLocalRandom.current();
        long sum = 0;
        int exceptionCount = 0;
        for (int i = 0; i < COUNT / 1_000; i++) {
            try {
                if (current.nextGaussian() < 0.99999) {
                    sum += instrumentedService.test().length();
                } else {
                    exceptionCount++;
                    sum += instrumentedService.throwsCheckedException();
                }
            } catch (Exception e) {
                sum++;
            }
        }

        System.out.printf("Sum: %d, exceptions: %d%n", sum, exceptionCount);
        assertThat(sum).isGreaterThanOrEqualTo(COUNT / 1_000 + 1);
    }
}
