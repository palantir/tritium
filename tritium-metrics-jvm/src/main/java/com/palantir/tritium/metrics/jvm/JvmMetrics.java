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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.JvmAttributeGaugeSet;
import com.codahale.metrics.Metric;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.ClassLoadingGaugeSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.google.common.collect.Maps;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.management.ManagementFactory;
import java.util.Map;

/**
 * {@link JvmMetrics} provides a standard set of metrics for debugging java services.
 */
public final class JvmMetrics {

    /**
     * Registers a default set of metrics.
     *
     * This includes {@link MetricRegistries#registerGarbageCollection(TaggedMetricRegistry)} and
     * {@link MetricRegistries#registerMemoryPools(TaggedMetricRegistry)}.
     *
     * @param registry metric registry
     */
    public static void register(TaggedMetricRegistry registry) {
        Preconditions.checkNotNull(registry, "TaggedMetricRegistry is required");
        MetricRegistries.registerGarbageCollection(registry);
        MetricRegistries.registerMemoryPools(registry);
        Jdk9CompatibleFileDescriptorRatioGauge.register(registry);
        OperatingSystemMetrics.register(registry);
        SafepointMetrics.register(registry);
        InternalJvmMetrics metrics = InternalJvmMetrics.of(registry);
        registerAttributes(metrics);
        MetricRegistries.registerAll(registry, "jvm.buffers",
                new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()));
        MetricRegistries.registerAll(registry, "jvm.classloader", new ClassLoadingGaugeSet());
        MetricRegistries.registerAll(registry, "jvm.memory", () -> Maps.filterKeys(
                // Memory pool metrics are already provided by MetricRegistries.registerMemoryPools
                new MemoryUsageGaugeSet().getMetrics(), name -> !name.startsWith("pools")));
        MetricRegistries.registerAll(registry, "jvm.threads", new ThreadStatesGaugeSet());
    }

    private static void registerAttributes(InternalJvmMetrics metrics) {
        Map<String, Metric> jvmAttributes = new JvmAttributeGaugeSet().getMetrics();
        metrics.attributeUptime()
                .javaSpecificationVersion(System.getProperty("java.specification.version", "unknown"))
                .javaVersion(System.getProperty("java.version", "unknown"))
                .javaVersionDate(System.getProperty("java.version.date", "unknown"))
                .javaRuntimeVersion(System.getProperty("java.runtime.version", "unknown"))
                .javaVendorVersion(System.getProperty("java.vendor.version", "unknown"))
                .javaVmVendor(System.getProperty("java.vm.vendor", "unknown"))
                .build(gauge(jvmAttributes, "uptime"));
    }

    private static Gauge<?> gauge(Map<String, Metric> metrics, String name) {
        Metric metric = Preconditions.checkNotNull(metrics.get(name),
                "Failed to find metric", SafeArg.of("name", name));
        if (metric instanceof Gauge) {
            return (Gauge<?>) metric;
        }
        throw new SafeIllegalStateException("Expected a gauge", SafeArg.of("type", metric.getClass()));
    }

    private JvmMetrics() {
        throw new UnsupportedOperationException();
    }
}
