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

package com.palantir.tritium.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.metrics.test.TestTaggedMetricRegistries;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class TaggedMetricsExecutorServiceTest {

    private static final String NAME = "name";

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testMetrics(TaggedMetricRegistry registry) throws Exception {
        ExecutorService executorService =
                MetricRegistries.instrument(registry, Executors.newSingleThreadExecutor(), NAME);
        ExecutorMetrics metrics = ExecutorMetrics.of(registry);

        assertThat(metrics.submitted(NAME).getCount()).isZero();
        assertThat(metrics.running(NAME).getCount()).isZero();
        assertThat(metrics.completed(NAME).getCount()).isZero();
        assertThat(metrics.duration(NAME).getCount()).isZero();
        assertThat(metrics.queuedDuration(NAME).getCount()).isZero();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);
        Future<String> future = executorService.submit(() -> {
            startLatch.countDown();
            finishLatch.await();
            return Thread.currentThread().getName();
        });

        executorService.shutdown();
        startLatch.await();

        assertThat(metrics.submitted(NAME).getCount()).isOne();
        assertThat(metrics.running(NAME).getCount()).isOne();
        assertThat(metrics.completed(NAME).getCount()).isZero();
        assertThat(metrics.duration(NAME).getCount()).isZero();
        assertThat(metrics.queuedDuration(NAME).getCount()).isOne();

        finishLatch.countDown();
        future.get();

        assertThat(metrics.submitted(NAME).getCount()).isOne();
        assertThat(metrics.running(NAME).getCount()).isZero();
        assertThat(metrics.completed(NAME).getCount()).isOne();
        assertThat(metrics.duration(NAME).getCount()).isOne();
        assertThat(metrics.queuedDuration(NAME).getCount()).isOne();
    }
}
