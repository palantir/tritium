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

package com.palantir.tritium.metrics.executor;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.MetricRegistry;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;

public final class TaggedMetricsExecutorServiceTest {

    private static final String NAME = "name";

    private static final MetricName SUBMITTED = metricName("submitted");
    private static final MetricName RUNNING = metricName("running");
    private static final MetricName COMPLETED = metricName("completed");
    private static final MetricName DURATION = metricName("duration");
    private static final MetricName QUEUED_DURATION = metricName("queued-duration");

    private TaggedMetricRegistry registry;
    private TaggedMetricsExecutorService executorService;

    @Before
    public void before() {
        registry = new DefaultTaggedMetricRegistry();
        executorService = TaggedMetricsExecutorService.create(
                Executors.newSingleThreadExecutor(),
                registry,
                NAME);
    }

    @Test
    public void testMetrics() throws Exception {
        assertThat(registry.getMetrics())
                .containsKeys(SUBMITTED, RUNNING, COMPLETED, DURATION, QUEUED_DURATION);

        assertThat(registry.meter(SUBMITTED).getCount()).isEqualTo(0);
        assertThat(registry.counter(RUNNING).getCount()).isEqualTo(0);
        assertThat(registry.meter(COMPLETED).getCount()).isEqualTo(0);
        assertThat(registry.timer(DURATION).getCount()).isEqualTo(0);
        assertThat(registry.timer(QUEUED_DURATION).getCount()).isEqualTo(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(1);
        Future<String> future = executorService.submit(() -> {
            startLatch.countDown();
            finishLatch.await();
            return Thread.currentThread().getName();
        });

        executorService.shutdown();
        startLatch.await();

        assertThat(registry.meter(SUBMITTED).getCount()).isEqualTo(1);
        assertThat(registry.counter(RUNNING).getCount()).isEqualTo(1);
        assertThat(registry.meter(COMPLETED).getCount()).isEqualTo(0);
        assertThat(registry.timer(DURATION).getCount()).isEqualTo(0);
        assertThat(registry.timer(QUEUED_DURATION).getCount()).isEqualTo(1);

        finishLatch.countDown();
        future.get();

        assertThat(registry.meter(SUBMITTED).getCount()).isEqualTo(1);
        assertThat(registry.counter(RUNNING).getCount()).isEqualTo(0);
        assertThat(registry.meter(COMPLETED).getCount()).isEqualTo(1);
        assertThat(registry.timer(DURATION).getCount()).isEqualTo(1);
        assertThat(registry.timer(QUEUED_DURATION).getCount()).isEqualTo(1);
    }

    private static MetricName metricName(String metricName) {
        return MetricName.builder()
                .safeName(MetricRegistry.name("executor", metricName))
                .putSafeTags("name", NAME)
                .build();
    }
}
