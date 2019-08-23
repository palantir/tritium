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

package com.palantir.tritium.metrics.jvm;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.RatioGauge;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JvmMetricsTest {

    private static final ImmutableSet<String> EXPECTED_NAMES = ImmutableSet.of(
            "jvm.attribute.name",
            "jvm.attribute.uptime",
            "jvm.attribute.vendor",
            "jvm.buffers.direct.capacity",
            "jvm.buffers.direct.count",
            "jvm.buffers.direct.used",
            "jvm.buffers.mapped.capacity",
            "jvm.buffers.mapped.count",
            "jvm.buffers.mapped.used",
            "jvm.classloader.loaded",
            "jvm.classloader.unloaded",
            "jvm.filedescriptor",
            "jvm.gc.time",
            "jvm.gc.count",
            "jvm.memory.heap.usage",
            "jvm.memory.pools.committed",
            "jvm.memory.total.max",
            "jvm.memory.pools.used",
            "jvm.memory.pools.init",
            "jvm.memory.pools.usage",
            "jvm.memory.pools.max",
            "jvm.memory.heap.committed",
            "jvm.memory.total.committed",
            "jvm.memory.total.used",
            "jvm.memory.total.init",
            "jvm.memory.pools.used-after-gc",
            "jvm.memory.heap.used",
            "jvm.memory.heap.init",
            "jvm.memory.non-heap.init",
            "jvm.memory.non-heap.usage",
            "jvm.memory.non-heap.used",
            "jvm.memory.non-heap.committed",
            "jvm.memory.non-heap.max",
            "jvm.memory.heap.max",
            "jvm.safepoint.time",
            "jvm.threads.timed_waiting.count",
            "jvm.threads.waiting.count",
            "jvm.threads.count",
            "jvm.threads.new.count",
            "jvm.threads.deadlock.count",
            "jvm.threads.daemon.count",
            "jvm.threads.runnable.count",
            "jvm.threads.terminated.count",
            "jvm.threads.blocked.count",
            "os.load.1",
            "os.load.norm.1",
            "process.cpu.utilization"
    );

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testExpectedMetrics() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        JvmMetrics.register(registry);
        assertThat(registry.getMetrics().keySet().stream()
                .map(MetricName::safeName)
                .collect(ImmutableSet.toImmutableSet()))
                .containsAll(EXPECTED_NAMES);
    }

    @Test
    public void testFileDescriptors() throws IOException {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        JvmMetrics.register(registry);
        Metric fileDescriptorsMetric =
                registry.getMetrics().get(MetricName.builder().safeName("jvm.filedescriptor").build());
        assertThat(fileDescriptorsMetric).isInstanceOf(RatioGauge.class);
        RatioGauge fileDescriptorsRatio = (RatioGauge) fileDescriptorsMetric;
        double initialDescriptorsRatio = fileDescriptorsRatio.getValue();
        List<Closeable> handles = new ArrayList<>();
        try {
            for (int i = 0; i < 100; i++) {
                handles.add(new FileOutputStream(temporaryFolder.newFile()));
            }
            assertThat(fileDescriptorsRatio.getValue()).isGreaterThan(initialDescriptorsRatio);
        } finally {
            for (Closeable closeable : handles) {
                closeable.close();
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSystemLoad() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        JvmMetrics.register(registry);
        Gauge<Double> systemLoadNormalized = (Gauge<Double>) registry.getMetrics()
                .get(MetricName.builder().safeName("os.load.norm.1").build());
        Gauge<Double> systemLoad = (Gauge<Double>) registry.getMetrics()
                .get(MetricName.builder().safeName("os.load.1").build());
        assertThat(systemLoad.getValue()).isPositive();
        assertThat(systemLoadNormalized.getValue()).isPositive();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCpuLoad() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        JvmMetrics.register(registry);
        Gauge<Double> processCpuLoad = (Gauge<Double>) registry.getMetrics()
                .get(MetricName.builder().safeName("process.cpu.utilization").build());
        double processLoad = processCpuLoad.getValue();
        assertThat(processLoad).isGreaterThanOrEqualTo(0D).isLessThanOrEqualTo(1D);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSafepoint() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        JvmMetrics.register(registry);
        Gauge<Long> safepointTime = (Gauge<Long>) registry.getMetrics()
                .get(MetricName.builder().safeName("jvm.safepoint.time").build());
        assertThat(safepointTime.getValue()).isNotNegative();
    }
}
