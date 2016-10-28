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

package com.palantir.tritium.tracing;

import com.google.common.base.Strings;
import com.palantir.remoting1.tracing.Tracer;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracingInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TracingInvocationEventHandler.class);

    private final String component;

    public TracingInvocationEventHandler(String component) {
        super(getEnabledSupplier(component));
        this.component = component;
    }

    @Override
    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        InvocationContext context = DefaultInvocationContext.of(instance, method, args);
        String operationName = getOperationName(method);
        Tracer.startSpan(operationName);
        return context;
    }

    private String getOperationName(Method method) {
        return Strings.isNullOrEmpty(component) ? method.getName() : component + '.' + method.getName();
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        debugIfNullContext(context);
        Tracer.completeSpan();
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        debugIfNullContext(context);
        // TODO (davids) add Error event
        Tracer.completeSpan();
    }

    private void debugIfNullContext(@Nullable InvocationContext context) {
        if (context == null) {
            LOGGER.debug("Encountered null metric context likely due to exception in preInvocation");
        }
    }

    static BooleanSupplier getEnabledSupplier(String component) {
        return getSystemPropertySupplier(component);
    }

}
