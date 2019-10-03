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

import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

final class OperatingSystemMetrics {
    private static final MetricName OS_LOAD_NORM_1 = MetricName.builder().safeName("os.load.norm.1").build();
    private static final MetricName OS_LOAD_1 = MetricName.builder().safeName("os.load.1").build();
    private static final MetricName PROCESS_CPU_UTILIZATION
            = MetricName.builder().safeName("process.cpu.utilization").build();
    private static final MetricName PROCESS_CPU_TIME
            = MetricName.builder().safeName("process.cpu.time.nanoseconds").build();

    static void register(TaggedMetricRegistry registry) {
        OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
        registry.gauge(OS_LOAD_NORM_1, () -> osMxBean.getSystemLoadAverage() / osMxBean.getAvailableProcessors());
        registry.gauge(OS_LOAD_1, osMxBean::getSystemLoadAverage);
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunBean = (com.sun.management.OperatingSystemMXBean) osMxBean;
            registry.gauge(PROCESS_CPU_UTILIZATION, sunBean::getProcessCpuLoad);
            registry.gauge(PROCESS_CPU_TIME, sunBean::getProcessCpuTime);
        }
    }

    private OperatingSystemMetrics() {
        throw new UnsupportedOperationException();
    }
}
