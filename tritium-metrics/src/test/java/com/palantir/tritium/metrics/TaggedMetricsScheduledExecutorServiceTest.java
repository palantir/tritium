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

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.SlidingWindowTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class TaggedMetricsScheduledExecutorServiceTest {

    private static final String NAME = "name";

    private static final MetricName SUBMITTED = metricName("submitted");
    private static final MetricName RUNNING = metricName("running");
    private static final MetricName COMPLETED = metricName("completed");
    private static final MetricName DURATION = metricName("duration");

    private static final MetricName SCHEDULED_ONCE = metricName("scheduled.once");
    private static final MetricName SCHEDULED_REPETITIVELY = metricName("scheduled.repetitively");
    private static final MetricName SCHEDULED_OVERRAN = metricName("scheduled.overrun");
    private static final MetricName SCHEDULED_PERCENT_OF_PERIOD = metricName("scheduled.percent-of-period");

    @Parameterized.Parameters
    public static ImmutableList<Supplier<Object>> data() {
        return ImmutableList.of(
                DefaultTaggedMetricRegistry::new,
                () -> new SlidingWindowTaggedMetricRegistry(30, TimeUnit.SECONDS));
    }

    @Parameterized.Parameter
    public Supplier<TaggedMetricRegistry> registrySupplier = () -> null;

    private ScheduledExecutorService executorService;
    private TaggedMetricRegistry registry;

    @Before
    public void before() {
        registry = registrySupplier.get();
        executorService = MetricRegistries.instrument(
                registry, Executors.newSingleThreadScheduledExecutor(), NAME);
    }

    @Test
    public void testMetrics() throws Exception {
        assertThat(registry.getMetrics())
                .containsKeys(SUBMITTED, RUNNING, COMPLETED, DURATION);

        assertThat(registry.meter(SUBMITTED).getCount()).isEqualTo(0);
        assertThat(registry.counter(RUNNING).getCount()).isEqualTo(0);
        assertThat(registry.meter(COMPLETED).getCount()).isEqualTo(0);
        assertThat(registry.timer(DURATION).getCount()).isEqualTo(0);

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

        finishLatch.countDown();
        future.get();

        assertThat(registry.meter(SUBMITTED).getCount()).isEqualTo(1);
        assertThat(registry.counter(RUNNING).getCount()).isEqualTo(0);
        assertThat(registry.meter(COMPLETED).getCount()).isEqualTo(1);
        assertThat(registry.timer(DURATION).getCount()).isEqualTo(1);
    }

    @Test
    public void testScheduledMetrics() throws Exception {
        assertThat(registry.getMetrics())
                .containsKeys(SCHEDULED_ONCE, SCHEDULED_REPETITIVELY);

        assertThat(registry.meter(SCHEDULED_ONCE).getCount()).isEqualTo(0);
        assertThat(registry.meter(SCHEDULED_REPETITIVELY).getCount()).isEqualTo(0);

        assertThat((Future<?>) executorService.schedule(() -> { }, 1L, TimeUnit.DAYS)).isNotNull();

        assertThat(registry.meter(SCHEDULED_ONCE).getCount()).isEqualTo(1);
        assertThat(registry.meter(SCHEDULED_REPETITIVELY).getCount()).isEqualTo(0);

        assertThat((Future<?>) executorService.scheduleAtFixedRate(() -> { }, 1L, 1L, TimeUnit.DAYS)).isNotNull();

        assertThat(registry.meter(SCHEDULED_ONCE).getCount()).isEqualTo(1);
        assertThat(registry.meter(SCHEDULED_REPETITIVELY).getCount()).isEqualTo(1);

        assertThat((Future<?>) executorService.scheduleWithFixedDelay(() -> { }, 1L, 1L, TimeUnit.DAYS)).isNotNull();

        assertThat(registry.meter(SCHEDULED_ONCE).getCount()).isEqualTo(1);
        assertThat(registry.meter(SCHEDULED_REPETITIVELY).getCount()).isEqualTo(2);
    }

    @Test
    public void testScheduledDurationMetrics() throws Exception {
        assertThat(registry.getMetrics())
                .containsKeys(SCHEDULED_OVERRAN, SCHEDULED_PERCENT_OF_PERIOD);

        assertThat(registry.counter(SCHEDULED_OVERRAN).getCount()).isEqualTo(0);
        assertThat(registry.histogram(SCHEDULED_PERCENT_OF_PERIOD).getCount()).isEqualTo(0);

        Semaphore startSemaphore = new Semaphore(0);
        Semaphore finishSemaphore = new Semaphore(1);

        assertThat((Future<?>) executorService.scheduleAtFixedRate(() -> {
            startSemaphore.release();
            finishSemaphore.acquireUninterruptibly();
        }, 0L, 1L, TimeUnit.MILLISECONDS)).isNotDone();

        startSemaphore.acquire(2);

        assertThat(registry.counter(SCHEDULED_OVERRAN).getCount()).isEqualTo(0);
        assertThat(registry.histogram(SCHEDULED_PERCENT_OF_PERIOD).getCount()).isEqualTo(1);

        TimeUnit.MILLISECONDS.sleep(2);
        finishSemaphore.release();
        startSemaphore.acquire();

        assertThat(registry.counter(SCHEDULED_OVERRAN).getCount()).isEqualTo(1);
        assertThat(registry.histogram(SCHEDULED_PERCENT_OF_PERIOD).getCount()).isEqualTo(2);
    }

    private static MetricName metricName(String metricName) {
        return MetricName.builder()
                .safeName(MetricRegistry.name("executor", metricName))
                .putSafeTags("executor", NAME)
                .build();
    }
}
