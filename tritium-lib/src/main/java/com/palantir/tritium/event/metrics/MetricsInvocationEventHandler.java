/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.codahale.metrics.MetricRegistry;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link com.palantir.tritium.event.InvocationEventHandler} that records method timing and failures using Dropwizard
 * metrics.
 */
public class MetricsInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsInvocationEventHandler.class);

    public static final String FAILURES_METRIC_NAME = "failures";

    private final MetricRegistry metricRegistry;
    private final String serviceName;

    public MetricsInvocationEventHandler(MetricRegistry metricRegistry, String serviceName) {
        super(getEnabledSupplier(serviceName));
        this.metricRegistry = checkNotNull(metricRegistry, "metricRegistry");
        this.serviceName = checkNotNull(serviceName, "serviceName");
    }

    private static BooleanSupplier getEnabledSupplier(final String serviceName) {
        return new BooleanSupplier() {
            @Override
            public boolean asBoolean() {
                return getSystemPropertySupplier(MetricsInvocationEventHandler.class).asBoolean()
                        || getSystemPropertySupplier(serviceName).asBoolean();
            }
        };
    }

    @Override
    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        if (context == null) {
            LOGGER.debug("Encountered null metric context likely due to exception in preInvocation");
            return;
        }
        metricRegistry.timer(getBaseMetricName(context))
                .update(System.nanoTime() - context.getStartTimeNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, Throwable cause) {
        if (context == null) {
            markGlobalFailure();
            LOGGER.debug("Encountered null metric context likely due to exception in preInvocation: {}", cause, cause);
            return;
        }

        markGlobalFailure();
        String failuresMetricName = MetricRegistry.name(getBaseMetricName(context), FAILURES_METRIC_NAME);
        metricRegistry.meter(failuresMetricName).mark();
        metricRegistry.meter(MetricRegistry.name(failuresMetricName, cause.getClass().getName())).mark();
    }

    private String getBaseMetricName(InvocationContext context) {
        return MetricRegistry.name(serviceName, context.getMethod().getName());
    }

    private void markGlobalFailure() {
        metricRegistry.meter(FAILURES_METRIC_NAME).mark();
    }

}
