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

package com.palantir.tritium.event.metrics;

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.codahale.metrics.MetricRegistry;
import com.palantir.logsafe.Safe;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** {@link InvocationEventHandler} that records method timing and failures using Dropwizard metrics. */
public final class MetricsInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final String FAILURES = "failures";

    private final MetricRegistry metricRegistry;

    @Safe
    private final String serviceName;

    @SuppressWarnings("WeakerAccess") // public API
    public MetricsInvocationEventHandler(MetricRegistry metricRegistry, @Safe String serviceName) {
        super(getEnabledSupplier(serviceName));
        this.metricRegistry = checkNotNull(metricRegistry, "metricRegistry");
        this.serviceName = checkNotNull(serviceName, "serviceName");
    }

    @SuppressWarnings("InconsistentOverloads")
    public MetricsInvocationEventHandler(
            MetricRegistry metricRegistry,
            Class<?> _serviceClass,
            @Safe String serviceName,
            @Safe @Nullable String _globalGroupPrefix) {
        super(getEnabledSupplier(serviceName));
        this.metricRegistry = checkNotNull(metricRegistry, "metricRegistry");
        this.serviceName = checkNotNull(serviceName, "serviceName");
    }

    @SuppressWarnings("WeakerAccess") // public API
    public MetricsInvocationEventHandler(
            MetricRegistry metricRegistry, Class<?> serviceClass, @Safe @Nullable String globalGroupPrefix) {
        this(metricRegistry, serviceClass, checkNotNull(serviceClass.getName()), globalGroupPrefix);
    }

    // explicitly qualifying BooleanSupplier types for deconfliction
    @SuppressWarnings({"NoFunctionalReturnType", "UnnecessarilyFullyQualified"})
    static java.util.function.BooleanSupplier getEnabledSupplier(String serviceName) {
        return InstrumentationProperties.getSystemPropertySupplier(serviceName);
    }

    @Override
    public InvocationContext preInvocation(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object _result) {
        debugIfNullContext(context);
        if (context != null) {
            updateTimer(context);
        }
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable _cause) {
        markGlobalFailure();
        debugIfNullContext(context);
        if (context != null) {
            metricRegistry.meter(getBaseMetricName(context) + '.' + FAILURES).mark();
        }
    }

    @SuppressWarnings("PreferJavaTimeOverload") // performance sensitive
    private void updateTimer(InvocationContext context) {
        long nanos = System.nanoTime() - context.getStartTimeNanos();
        metricRegistry.timer(getBaseMetricName(context)).update(nanos, TimeUnit.NANOSECONDS);
    }

    private String getBaseMetricName(InvocationContext context) {
        return serviceName + '.' + context.getMethod().getName();
    }

    private void markGlobalFailure() {
        metricRegistry.meter(FAILURES).mark();
    }
}
