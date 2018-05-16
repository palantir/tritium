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

package com.palantir.tritium;

import com.codahale.metrics.MetricRegistry;
import com.palantir.tritium.proxy.Instrumentation;
import com.palantir.tritium.tracing.TracingInvocationEventHandler;

/**
 * Provides static methods for Tritium instrumentation.
 */
public final class Tritium {

    private Tritium() {
        throw new UnsupportedOperationException();
    }

    /**
     * Return an instrumented proxy of the specified service interface and delegate that records aggregated invocation
     * metrics, Zipkin style traces, and performance trace logging.
     *
     * @param serviceInterface service interface
     * @param delegate delegate to instrument
     * @param metricRegistry metric registry
     * @return instrumented proxy implementing specified service interface
     */
    public static <T, U extends T> T instrument(Class<T> serviceInterface, U delegate, MetricRegistry metricRegistry) {
        return instrument(serviceInterface, delegate, metricRegistry, false);
    }

    public static <T, U extends T> T instrument(Class<T> serviceInterface, U delegate, MetricRegistry metricRegistry, boolean useTagsForMethods) {
        return Instrumentation.builder(serviceInterface, delegate)
                .withMetrics(metricRegistry)
                .withPerformanceTraceLogging()
                .withHandler(new TracingInvocationEventHandler(serviceInterface.getName()))
                .build();
    }
}
