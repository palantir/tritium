/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Timer;
import com.palantir.logsafe.Safe;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
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
 * An implementation of {@link AbstractInvocationEventHandler} whose purpose is to provide tagged metrics as
 * described in {@code invocation-metrics.yml}.
 */
public final class TaggedMetricInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private final String serviceName;
    private final ConcurrentMap<Method, Timer> timerCache = new ConcurrentHashMap<>();
    private final Function<Method, Timer> onSuccessTimerMappingFunction;
    private final InvocationMetrics metrics;

    public static InvocationEventHandler<InvocationContext> of(
            TaggedMetricRegistry taggedMetricRegistry, @Safe String serviceName) {
        return new TaggedMetricInvocationEventHandler(taggedMetricRegistry, serviceName);
    }

    private TaggedMetricInvocationEventHandler(TaggedMetricRegistry taggedMetricRegistry, String serviceName) {
        super(getEnabledSupplier(serviceName));
        this.metrics = InvocationMetrics.of(taggedMetricRegistry);
        this.serviceName = checkNotNull(serviceName, "serviceName");
        this.onSuccessTimerMappingFunction = method -> metrics.success()
                .serviceName(serviceName)
                .methodName(method.getName())
                .build();
    }

    @SuppressWarnings("NoFunctionalReturnType") // helper
    private static BooleanSupplier getEnabledSupplier(String serviceName) {
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
            long nanos = System.nanoTime() - context.getStartTimeNanos();
            getSuccessTimer(context.getMethod()).update(nanos, TimeUnit.NANOSECONDS);
        }
    }

    private Timer getSuccessTimer(Method method) {
        return timerCache.computeIfAbsent(method, onSuccessTimerMappingFunction);
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable _cause) {
        debugIfNullContext(context);
        if (context != null) {
            metrics.failure()
                    .serviceName(serviceName)
                    .methodName(context.getMethod().getName())
                    .build()
                    .mark();
        }
    }
}
