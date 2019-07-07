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

import com.codahale.metrics.RatioGauge;
import com.palantir.logsafe.SafeArg;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.sun.management.UnixOperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A gauge to report the ratio of open file descriptors.
 *
 * Does the same thing as {@link com.codahale.metrics.jvm.FileDescriptorRatioGauge} but compatible with Java 9+
 */
final class Jdk9CompatibleFileDescriptorRatioGauge {

    private static final Logger log = LoggerFactory.getLogger(Jdk9CompatibleFileDescriptorRatioGauge.class);

    static void register(TaggedMetricRegistry registry) {
        OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
        if (osMxBean instanceof UnixOperatingSystemMXBean) {
            UnixOperatingSystemMXBean unix = (UnixOperatingSystemMXBean) osMxBean;
            registry.gauge(MetricName.builder().safeName("jvm.filedescriptor").build(), new RatioGauge() {
                @Override
                protected Ratio getRatio() {
                    return Ratio.of(unix.getOpenFileDescriptorCount(), unix.getMaxFileDescriptorCount());
                }
            });
        } else {
            log.debug("OperatingSystemMXBean is not a UnixOperatingSystemMXBean, file descriptors will not be "
                    + "reported. Type is {}", SafeArg.of("osMxBeanType", osMxBean.getClass().getSimpleName()));
        }
    }

    private Jdk9CompatibleFileDescriptorRatioGauge() {
        throw new UnsupportedOperationException();
    }
}
