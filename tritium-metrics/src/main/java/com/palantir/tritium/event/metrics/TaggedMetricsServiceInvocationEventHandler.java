/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Timer;
import com.palantir.logsafe.Safe;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.metrics.InstrumentationMetrics.Invocation_Result;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An implementation of {@link AbstractInvocationEventHandler} whose purpose is to provide tagged metrics for classes
 * which look like services.
 *
 * <p>Specifically, this class will generate metrics with the following parameters:
 *
 * <ul>
 *   <li>Metric Name: the service name supplied to the constructor
 *   <li>Tag - service-name: The service name
 *   <li>Tag - endpoint: The name of the method that was invoked
 *   <li>Tag - result: {@code success} if the method completed normally or {@code failure} if the method completed
 *   exceptionally.
 * </ul>
 */
public class TaggedMetricsServiceInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private final ConcurrentMap<Method, Timer> successTimerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Method, Timer> failureTimerCache = new ConcurrentHashMap<>();
    private final Function<Method, Timer> onSuccessTimerMappingFunction;
    private final Function<Method, Timer> onFailureTimerMappingFunction;

    public TaggedMetricsServiceInvocationEventHandler(
            TaggedMetricRegistry taggedMetricRegistry, @Safe String serviceName) {
        super(getEnabledSupplier(serviceName));
        InstrumentationMetrics metrics = InstrumentationMetrics.of(taggedMetricRegistry);
        this.onSuccessTimerMappingFunction = method -> metrics.invocation()
                .serviceName(serviceName)
                .endpoint(method.getName())
                .result(Invocation_Result.SUCCESS)
                .build();
        this.onFailureTimerMappingFunction = method -> metrics.invocation()
                .serviceName(serviceName)
                .endpoint(method.getName())
                .result(Invocation_Result.FAILURE)
                .build();
    }

    @SuppressWarnings("NoFunctionalReturnType") // helper
    private static BooleanSupplier getEnabledSupplier(@Safe String serviceName) {
        return InstrumentationProperties.getSystemPropertySupplier(serviceName);
    }

    @Override
    public final InvocationContext preInvocation(
            @Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    @SuppressWarnings("PreferJavaTimeOverload") // performance sensitive
    public final void onSuccess(@Nullable InvocationContext context, @Nullable Object _result) {
        debugIfNullContext(context);
        if (context != null) {
            long nanos = System.nanoTime() - context.getStartTimeNanos();
            getSuccessTimer(context.getMethod()).update(nanos, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public final void onFailure(@Nullable InvocationContext context, @Nonnull Throwable _cause) {
        debugIfNullContext(context);
        if (context != null) {
            long nanos = System.nanoTime() - context.getStartTimeNanos();
            getFailureTimer(context.getMethod()).update(nanos, TimeUnit.NANOSECONDS);
        }
    }

    private Timer getSuccessTimer(Method method) {
        return successTimerCache.computeIfAbsent(method, onSuccessTimerMappingFunction);
    }

    private Timer getFailureTimer(Method method) {
        return failureTimerCache.computeIfAbsent(method, onFailureTimerMappingFunction);
    }
}
