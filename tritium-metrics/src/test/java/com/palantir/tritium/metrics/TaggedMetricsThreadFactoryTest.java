/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.logsafe.exceptions.SafeNullPointerException;
import com.palantir.nylon.threads.VirtualThreads;
import com.palantir.nylon.threads.VirtualThreads.VirtualThreadSupport;
import com.palantir.tritium.metrics.ExecutorMetrics.ThreadsCreated_ThreadType;
import com.palantir.tritium.metrics.ExecutorMetrics.ThreadsRunning_ThreadType;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class TaggedMetricsThreadFactoryTest {

    @Test
    void testInstrumentation() {
        String name = "name";
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        ThreadFactory delegate = new ThreadFactoryBuilder()
                .setNameFormat("test-%d")
                .setDaemon(true)
                .build();
        ThreadFactory instrumented = MetricRegistries.instrument(registry, delegate, name);
        ExecutorMetrics metrics = ExecutorMetrics.of(registry);
        Counter running = metrics.threadsRunning()
                .executor(name)
                .threadType(ThreadsRunning_ThreadType.PLATFORM)
                .build();
        Meter created = metrics.threadsCreated()
                .executor(name)
                .threadType(ThreadsCreated_ThreadType.PLATFORM)
                .build();
        assertThat(running.getCount()).isZero();
        assertThat(created.getCount()).isZero();
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = instrumented.newThread(() -> Uninterruptibles.awaitUninterruptibly(latch));
        assertThat(created.getCount()).isOne();
        // thread has not started yet
        assertThat(running.getCount()).isZero();
        thread.start();
        // Allow the thread to start in the background
        Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(created.getCount()).isOne();
            assertThat(running.getCount()).isOne();
        });
        latch.countDown();
        Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(created.getCount()).isOne();
            assertThat(running.getCount()).isZero();
        });
        Awaitility.waitAtMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(thread.isAlive()).isFalse());
    }

    @Test
    void testVirtualThreadInstrumentation() {
        String name = "name";
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        Optional<VirtualThreadSupport> maybeVirtualThreadSupport = VirtualThreads.get();
        assumeThat(maybeVirtualThreadSupport)
                .as("Virtual thread tests require a runtime environment with virtual thread support")
                .isPresent();
        ThreadFactory delegate = maybeVirtualThreadSupport
                .orElseThrow()
                .ofVirtual()
                .name("virtual-test-", 0)
                .factory();
        ThreadFactory instrumented = MetricRegistries.instrument(registry, delegate, name);
        ExecutorMetrics metrics = ExecutorMetrics.of(registry);
        Counter running = metrics.threadsRunning()
                .executor(name)
                .threadType(ThreadsRunning_ThreadType.VIRTUAL)
                .build();
        Meter created = metrics.threadsCreated()
                .executor(name)
                .threadType(ThreadsCreated_ThreadType.VIRTUAL)
                .build();
        assertThat(running.getCount()).isZero();
        assertThat(created.getCount()).isZero();
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = instrumented.newThread(() -> Uninterruptibles.awaitUninterruptibly(latch));
        assertThat(created.getCount()).isOne();
        // thread has not started yet
        assertThat(running.getCount()).isZero();
        thread.start();
        // Allow the thread to start in the background
        Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(created.getCount()).isOne();
            assertThat(running.getCount()).isOne();
        });
        latch.countDown();
        Awaitility.waitAtMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(created.getCount()).isOne();
            assertThat(running.getCount()).isZero();
        });
        Awaitility.waitAtMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(thread.isAlive()).isFalse());
    }

    @Test
    @SuppressWarnings("NullAway")
    void testNullThreadFactory() {
        assertThatThrownBy(() ->
                        MetricRegistries.instrument(new DefaultTaggedMetricRegistry(), (ThreadFactory) null, "name"))
                .isInstanceOf(SafeNullPointerException.class)
                .hasMessageContaining("ThreadFactory is required");
    }

    @Test
    @SuppressWarnings("NullAway")
    void testNullName() {
        assertThatThrownBy(() -> MetricRegistries.instrument(
                        new DefaultTaggedMetricRegistry(),
                        new ThreadFactoryBuilder().setNameFormat("test-%d").build(),
                        null))
                .isInstanceOf(SafeNullPointerException.class)
                .hasMessageContaining("Name is required");
    }

    @Test
    @SuppressWarnings("NullAway")
    void testNullRegistry() {
        assertThatThrownBy(() -> MetricRegistries.instrument(
                        null,
                        new ThreadFactoryBuilder().setNameFormat("test-%d").build(),
                        "name"))
                .isInstanceOf(SafeNullPointerException.class)
                .hasMessageContaining("TaggedMetricRegistry");
    }
}
