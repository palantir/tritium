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

package com.palantir.tritium.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.metrics.test.TestTaggedMetricRegistries;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TaggedMetricsScheduledThreadPoolExecutorTest {
    private static final String NAME = "name";

    @ParameterizedTest
    @MethodSource(TestTaggedMetricRegistries.REGISTRIES)
    @SuppressWarnings("DangerousThreadPoolExecutorUsage")
    void testMetrics(TaggedMetricRegistry registry) throws Exception {
        ScheduledExecutorService scheduledExecutorService =
                new TaggedMetricsScheduledThreadPoolExecutor(1, Executors.defaultThreadFactory(), registry, NAME);
        scheduledExecutorService = MetricRegistries.instrument(registry, scheduledExecutorService, NAME);
        ExecutorMetrics metrics = ExecutorMetrics.of(registry);

        assertThat(metrics.running(NAME).getCount()).isZero();
        assertThat(metrics.duration(NAME).getCount()).isZero();
        assertThat(metrics.queuedDuration(NAME).getCount()).isZero();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishFirstTask = new CountDownLatch(1);
        Future<String> firstTask = scheduledExecutorService.submit(() -> {
            startLatch.countDown();
            finishFirstTask.await();
            return Thread.currentThread().getName();
        });
        Future<String> secondTask =
                scheduledExecutorService.submit(() -> Thread.currentThread().getName());
        scheduledExecutorService.shutdown();

        startLatch.await();

        assertThat(metrics.running(NAME).getCount()).isOne();
        assertThat(metrics.duration(NAME).getCount()).isZero();

        Thread.sleep(1L); // the precision of TaggedMetricsScheduledThreadPoolExecutor is in ms.
        finishFirstTask.countDown();
        firstTask.get();
        secondTask.get();

        assertThat(metrics.running(NAME).getCount()).isZero();
        assertThat(metrics.duration(NAME).getCount()).isEqualTo(2);
        // usually 1 (because the 2nd task was delayed), but we cannot know if the 1st task also got delayed.
        assertThat(metrics.queuedDuration(NAME).getCount()).isGreaterThan(0);
    }
}
