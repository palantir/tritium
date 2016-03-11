/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium;

import com.codahale.metrics.MetricRegistry;
import com.palantir.tritium.proxy.Instrumentation;

/**
 * Provides static methods for Tritium instrumentation.
 */
public final class Tritium {

    private Tritium() {
        throw new UnsupportedOperationException();
    }

    /**
     * Return an instrumented proxy of the specified service interface and delegate that records aggregated invocation
     * metrics and performance trace logging.
     *
     * @param serviceInterface service interface
     * @param delegate delegate to instrument
     * @param metricRegistry metric registry
     * @return instrumented proxy implementing specified service interface
     */
    public static <T, U extends T> T instrument(Class<T> serviceInterface, U delegate, MetricRegistry metricRegistry) {
        return Instrumentation.builder(serviceInterface, delegate)
                .withMetrics(metricRegistry)
                .withPerformanceTraceLogging()
                .build();
    }

}
