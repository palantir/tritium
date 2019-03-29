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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.event.metrics.annotations.AnnotationHelper;
import com.palantir.tritium.event.metrics.annotations.MetricGroup;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@link InvocationEventHandler} that records method timing and failures using Dropwizard metrics.
 */
public final class MetricsInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final String FAILURES = "failures";

    private final MetricRegistry metricRegistry;
    private final String serviceName;

    //consider creating annotation handlers as separate objects
    private final Map<AnnotationHelper.MethodSignature, String> metricGroups;
    @Nullable private final String globalGroupPrefix;

    @SuppressWarnings("WeakerAccess") // public API
    public MetricsInvocationEventHandler(MetricRegistry metricRegistry, String serviceName) {
        super(getEnabledSupplier(serviceName));
        this.metricRegistry = checkNotNull(metricRegistry, "metricRegistry");
        this.serviceName = checkNotNull(serviceName, "serviceName");
        this.metricGroups = ImmutableMap.of();
        this.globalGroupPrefix = null;
    }

    public MetricsInvocationEventHandler(
            MetricRegistry metricRegistry, Class serviceClass, String serviceName, @Nullable String globalGroupPrefix) {
        super(getEnabledSupplier(serviceName));
        this.metricRegistry = checkNotNull(metricRegistry, "metricRegistry");
        this.serviceName = checkNotNull(serviceName, "serviceName");
        this.metricGroups = createMethodGroupMapping(checkNotNull(serviceClass));
        this.globalGroupPrefix = Strings.emptyToNull(globalGroupPrefix);
    }

    @SuppressWarnings("WeakerAccess") // public API
    public MetricsInvocationEventHandler(
            MetricRegistry metricRegistry, Class serviceClass, @Nullable String globalGroupPrefix) {
        this(metricRegistry, serviceClass, checkNotNull(serviceClass.getName()), globalGroupPrefix);
    }

    private static Map<AnnotationHelper.MethodSignature, String> createMethodGroupMapping(Class<?> serviceClass) {
        ImmutableMap.Builder<AnnotationHelper.MethodSignature, String> builder = ImmutableMap.builder();

        MetricGroup classGroup = AnnotationHelper.getSuperTypeAnnotation(serviceClass, MetricGroup.class);

        for (Method method : serviceClass.getMethods()) {
            AnnotationHelper.MethodSignature sig = AnnotationHelper.MethodSignature.of(method);
            MetricGroup methodGroup = AnnotationHelper.getMethodAnnotation(MetricGroup.class, serviceClass, sig);

            if (methodGroup != null) {
                builder.put(sig, methodGroup.value());
            } else if (classGroup != null) {
                builder.put(sig, classGroup.value());
            }
        }

        return builder.build();
    }

    static java.util.function.BooleanSupplier getEnabledSupplier(String serviceName) {
        return InstrumentationProperties.getSystemPropertySupplier(serviceName);
    }

    @Override
    public InvocationContext preInvocation(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        debugIfNullContext(context);
        if (context != null) {
            long nanos = updateTimer(context);
            handleSuccessAnnotations(context, nanos);
        }
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        markGlobalFailure();
        debugIfNullContext(context);
        if (context != null) {
            String failuresMetricName = MetricRegistry.name(getBaseMetricName(context), FAILURES);
            metricRegistry.meter(failuresMetricName).mark();
            metricRegistry.meter(MetricRegistry.name(failuresMetricName, cause.getClass().getName())).mark();
            long nanos = updateTimer(context);
            handleFailureAnnotations(context, nanos);
        }
    }

    private long updateTimer(InvocationContext context) {
        long nanos = System.nanoTime() - context.getStartTimeNanos();
        metricRegistry.timer(getBaseMetricName(context))
                .update(nanos, TimeUnit.NANOSECONDS);
        return nanos;
    }

    private String getBaseMetricName(InvocationContext context) {
        return MetricRegistry.name(serviceName, context.getMethod().getName());
    }

    private void markGlobalFailure() {
        metricRegistry.meter(FAILURES).mark();
    }

    private void handleSuccessAnnotations(InvocationContext context, long nanos) {
        String metricName = getAnnotatedMetricName(context);
        if (metricName != null) {
            metricRegistry.timer(MetricRegistry.name(serviceName, metricName))
                    .update(nanos, TimeUnit.NANOSECONDS);

            if (globalGroupPrefix != null) {
                metricRegistry.timer(MetricRegistry.name(globalGroupPrefix, metricName))
                        .update(nanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    private void handleFailureAnnotations(InvocationContext context, long nanos) {
        String metricName = getAnnotatedMetricName(context);
        if (metricName != null) {
            metricRegistry.timer(MetricRegistry.name(serviceName, metricName, FAILURES))
                    .update(nanos, TimeUnit.NANOSECONDS);

            if (globalGroupPrefix != null) {
                metricRegistry.timer(MetricRegistry.name(globalGroupPrefix, metricName, FAILURES))
                        .update(nanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    @Nullable
    private String getAnnotatedMetricName(InvocationContext context) {
        return metricGroups.get(AnnotationHelper.MethodSignature.of(context.getMethod()));
    }
}
