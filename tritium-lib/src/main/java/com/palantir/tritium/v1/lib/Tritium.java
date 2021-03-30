/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.v1.lib;

import com.codahale.metrics.MetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.v1.tracing.event.TracingInvocationEventHandler;

/** Provides static methods for Tritium instrumentation. */
public final class Tritium {

    private Tritium() {
        throw new UnsupportedOperationException();
    }

    /**
     * Return an instrumented proxy of the specified service interface and delegate that records aggregated invocation
     * metrics, and Zipkin style traces.
     *
     * NB: Previous versions of this method included performance logging. With changes in Java9+ the method
     * of constructing performance logs became a slow operation. So this is no longer included.
     * See: https://github.com/apache/logging-log4j2/pull/475 for details
     *
     * @param serviceInterface service interface
     * @param delegate delegate to instrument
     * @param metricRegistry metric registry
     * @return instrumented proxy implementing specified service interface
     */
    public static <T, U extends T> T instrument(Class<T> serviceInterface, U delegate, MetricRegistry metricRegistry) {
        return Instrumentation.builder(serviceInterface, delegate)
                .withMetrics(metricRegistry)
                .withHandler(TracingInvocationEventHandler.create(serviceInterface.getName()))
                .build();
    }

    /**
     * Return an instrumented proxy of the specified service interface, and delegate that records aggregated invocation
     * metrics, and Zipkin style traces.
     *
     * NB: Previous versions of this method included performance logging. With changes in Java9+ the method
     * of constructing performance logs became a slow operation. So this is no longer included.
     * See: https://github.com/apache/logging-log4j2/pull/475 for details
     *
     * @param serviceInterface The service class to instrument
     * @param delegate Delegate to instrument
     * @param metricRegistry A {@link TaggedMetricRegistry} for registering metrics
     * @return instrumented proxy implementing specified service interface
     */
    public static <T, U extends T> T instrument(
            Class<T> serviceInterface, U delegate, TaggedMetricRegistry metricRegistry) {
        return Instrumentation.builder(serviceInterface, delegate)
                .withTaggedMetrics(metricRegistry)
                .withHandler(TracingInvocationEventHandler.create(serviceInterface.getName()))
                .build();
    }
}
