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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class TaggedMetricsScheduledExecutorServiceTest {

    private static final String NAME = "name";

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testMetrics(TaggedMetricRegistry registry) throws Exception {
        ScheduledExecutorService executorService =
                MetricRegistries.instrument(registry, Executors.newSingleThreadScheduledExecutor(), NAME);
        ExecutorMetrics metrics = ExecutorMetrics.of(registry);

        assertThat(metrics.submitted(NAME).getCount()).isZero();
        assertThat(metrics.running(NAME).getCount()).isZero();
        assertThat(metrics.duration(NAME).getCount()).isZero();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);
        Future<String> future = executorService.submit(() -> {
            startLatch.countDown();
            finishLatch.await();
            return Thread.currentThread().getName();
        });

        executorService.shutdown();
        startLatch.await();

        assertThat(metrics.submitted(NAME).getCount())
                .as("Scheduled executors don't produce 'submitted'")
                .isZero();
        assertThat(metrics.running(NAME).getCount()).isOne();
        assertThat(metrics.duration(NAME).getCount()).isZero();

        finishLatch.countDown();
        future.get();

        assertThat(metrics.submitted(NAME).getCount())
                .as("Scheduled executors don't produce 'submitted'")
                .isZero();
        assertThat(metrics.running(NAME).getCount()).isZero();
        assertThat(metrics.duration(NAME).getCount()).isOne();
    }

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    void testScheduledDurationMetrics(TaggedMetricRegistry registry) throws Exception {
        ScheduledExecutorService executorService =
                MetricRegistries.instrument(registry, Executors.newSingleThreadScheduledExecutor(), NAME);
        ExecutorMetrics metrics = ExecutorMetrics.of(registry);

        assertThat(metrics.scheduledOverrun(NAME).getCount()).isZero();

        Semaphore startSemaphore = new Semaphore(0);
        Semaphore finishSemaphore = new Semaphore(1);

        assertThat((Future<?>) executorService.scheduleAtFixedRate(
                        () -> {
                            startSemaphore.release();
                            finishSemaphore.acquireUninterruptibly();
                        },
                        0L,
                        1L,
                        TimeUnit.MILLISECONDS))
                .isNotDone();

        startSemaphore.acquire(2);

        assertThat(metrics.scheduledOverrun(NAME).getCount()).isZero();

        TimeUnit.MILLISECONDS.sleep(2);
        finishSemaphore.release();
        startSemaphore.acquire();

        assertThat(metrics.scheduledOverrun(NAME).getCount()).isOne();
    }
}
