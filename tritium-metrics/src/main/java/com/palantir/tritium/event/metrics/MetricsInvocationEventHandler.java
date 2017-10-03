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
import com.palantir.tritium.api.event.InvocationContext;
import com.palantir.tritium.api.event.InvocationEventHandler;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.metrics.annotations.MetricGroupHandler;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link InvocationEventHandler} that records method timing and failures using Dropwizard
 * metrics.
 */
public final class MetricsInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger logger = LoggerFactory.getLogger(MetricsInvocationEventHandler.class);

    private final MetricRegistry metricRegistry;
    private final String serviceName;

    //Consider generalizing a common interface when we want to support many AnnotationHandlers
    @Nullable private final MetricGroupHandler metricGroupHandler;

    public MetricsInvocationEventHandler(MetricRegistry metricRegistry, String serviceName) {
        super(getEnabledSupplier(serviceName));
        this.metricRegistry = checkNotNull(metricRegistry, "metricRegistry");
        this.serviceName = checkNotNull(serviceName, "serviceName");
        this.metricGroupHandler = null;
    }

    public MetricsInvocationEventHandler(
            MetricRegistry metricRegistry, Class serviceClass, String serviceName, @Nullable String globalGroupPrefix) {
        super(getEnabledSupplier(serviceName));
        this.metricRegistry = checkNotNull(metricRegistry, "metricRegistry");
        this.serviceName = checkNotNull(serviceName, "serviceName");
        this.metricGroupHandler = MetricGroupHandler.of(metricRegistry, serviceClass, serviceName, globalGroupPrefix);
    }

    public MetricsInvocationEventHandler(
            MetricRegistry metricRegistry, Class serviceClass, @Nullable String globalGroupPrefix) {
       this(metricRegistry, serviceClass, checkNotNull(serviceClass.getName()), globalGroupPrefix);
    }

    private static String failuresMetricName() {
        return "failures";
    }

    static BooleanSupplier getEnabledSupplier(final String serviceName) {
        return getSystemPropertySupplier(serviceName);
    }

    @Override
    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        if (context == null) {
            logger.debug("Encountered null metric context likely due to exception in preInvocation");
            return;
        }
        long nanos = context.markCompleteTimeNanos() - context.getStartTimeNanos();
        metricRegistry.timer(getBaseMetricName(context))
                .update(nanos, TimeUnit.NANOSECONDS);

        if (metricGroupHandler != null) {
            metricGroupHandler.onSuccess(context);
        }
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        if (context == null) {
            markGlobalFailure();
            logger.debug("Encountered null metric context likely due to exception in preInvocation: {}", cause, cause);
            return;
        }

        markGlobalFailure();
        String failuresMetricName = MetricRegistry.name(getBaseMetricName(context), failuresMetricName());
        metricRegistry.meter(failuresMetricName).mark();
        metricRegistry.meter(MetricRegistry.name(failuresMetricName, cause.getClass().getName())).mark();

        if (metricGroupHandler != null) {
            metricGroupHandler.onFailure(context);
        }
    }

    private String getBaseMetricName(InvocationContext context) {
        return MetricRegistry.name(serviceName, context.getMethod().getName());
    }

    private void markGlobalFailure() {
        metricRegistry.meter(failuresMetricName()).mark();
    }

}
