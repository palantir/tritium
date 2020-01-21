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
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Report duration spent waiting at safepoints. This could indicate badness in terms of STW GCing, or biased locking
 * going badly. See https://stackoverflow.com/questions/29666057/analyzing-gc-logs/29673564#29673564 for details. This
 * essentially provides the information of '+PrintGCApplicationStoppedTime' programmatically.
 */
final class SafepointMetrics {
    private static final Logger log = LoggerFactory.getLogger(SafepointMetrics.class);

    // The reflection is so that we can use this on non-Hotspot JVMs
    @SuppressWarnings("LiteralClassName")
    static void register(TaggedMetricRegistry registry) {
        try {
            Class<?> managementFactoryHelper = Class.forName("sun.management.ManagementFactoryHelper");
            Method getHotspotRuntimeMBean = managementFactoryHelper.getMethod("getHotspotRuntimeMBean");
            Object hotspotRuntimeMBean = getHotspotRuntimeMBean.invoke(null);
            Method getTotalSafepointTime = hotspotRuntimeMBean.getClass().getMethod("getTotalSafepointTime");
            getTotalSafepointTime.setAccessible(true);
            Gauge<Long> gauge = () -> (Long) invoke(getTotalSafepointTime, hotspotRuntimeMBean);
            InternalJvmMetrics.of(registry).safepointTime(gauge);
        } catch (ReflectiveOperationException e) {
            log.info("Could not get the total safepoint time, these metrics will not be registered.", e);
        }
    }

    private static Object invoke(Method method, Object object) {
        try {
            return method.invoke(object);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    private SafepointMetrics() {
        throw new UnsupportedOperationException();
    }
}
