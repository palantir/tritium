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

package com.palantir.tritium.tracing;

import com.google.common.base.Strings;
import com.palantir.logsafe.SafeArg;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationContext;
import java.lang.reflect.Method;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TracingInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger logger = LoggerFactory.getLogger(TracingInvocationEventHandler.class);

    private final String component;
    private final boolean shouldUseJavaTracing;

    private static boolean shouldUseJavaTracing() {
        // ugly but avoids double traces if old http-remoting3 tracing does not delegate to java-tracing
        Runnable wrappedTrace = com.palantir.remoting3.tracing.Tracers.wrap(() -> {
        });
        String expectedTracingPackage = com.palantir.tracing.Tracers.class.getPackage().getName();
        String actualTracingPackage = wrappedTrace.getClass().getPackage().getName();
        boolean foundMultipleTracingImplementations = !Objects.equals(expectedTracingPackage, actualTracingPackage);
        if (foundMultipleTracingImplementations) {
            logger.error("Multiple tracing implementations detected, expected '{}' but found '{}',"
                            + " using legacy remoting3 tracing for backward compatibility",
                    SafeArg.of("expectedPackage", expectedTracingPackage),
                    SafeArg.of("actualPackage", actualTracingPackage));
            return false;
        }
        return true;
    }

    public TracingInvocationEventHandler(String component) {
        super((java.util.function.BooleanSupplier) getEnabledSupplier(component));
        this.component = component;
        this.shouldUseJavaTracing = shouldUseJavaTracing();
    }

    @Override
    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        InvocationContext context = DefaultInvocationContext.of(instance, method, args);
        String operationName = getOperationName(method);
        startSpan(operationName);
        return context;
    }

    private String getOperationName(Method method) {
        return Strings.isNullOrEmpty(component) ? method.getName() : component + '.' + method.getName();
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        debugIfNullContext(context);
        completeSpan();
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        debugIfNullContext(context);
        // TODO(davids): add Error event
        completeSpan();
    }

    private void startSpan(String operationName) {
        if (shouldUseJavaTracing) {
            com.palantir.tracing.Tracer.startSpan(operationName);
        } else {
            com.palantir.remoting3.tracing.Tracer.startSpan(operationName);
        }
    }

    private void completeSpan() {
        if (shouldUseJavaTracing) {
            com.palantir.tracing.Tracer.fastCompleteSpan();
        } else {
            com.palantir.remoting3.tracing.Tracer.fastCompleteSpan();
        }
    }

    private static void debugIfNullContext(@Nullable InvocationContext context) {
        if (context == null) {
            logger.debug("Encountered null metric context likely due to exception in preInvocation");
        }
    }

    static BooleanSupplier getEnabledSupplier(String component) {
        return InstrumentationProperties.getSystemPropertySupplier(component);
    }

}
