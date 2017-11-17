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

package com.palantir.tritium.brave;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.SpanId;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationProperties;
import com.palantir.tritium.event.InvocationContext;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Constants;

public final class BraveLocalTracingInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger logger = LoggerFactory.getLogger(BraveLocalTracingInvocationEventHandler.class);

    private final Brave brave;
    private final String component;

    public BraveLocalTracingInvocationEventHandler(String component, Brave brave) {
        super(getEnabledSupplier(component));
        this.brave = brave;
        this.component = component;
    }

    @Override
    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        InvocationContext context = DefaultInvocationContext.of(instance, method, args);
        LocalTracer tracer = brave.localTracer();
        SpanId span = tracer.startNewSpan(component, method.getName());
        if (span != null) {
            for (int i = 0; i < args.length; i++) {
                tracer.submitBinaryAnnotation("arg" + i, String.valueOf(args[i]));
            }
        }
        return context;
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        debugIfNullContext(context);
        brave.localTracer().finishSpan();
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        debugIfNullContext(context);
        brave.localTracer().submitBinaryAnnotation(Constants.ERROR, String.valueOf(cause));
        brave.localTracer().finishSpan();
    }

    private void debugIfNullContext(@Nullable InvocationContext context) {
        if (context == null) {
            logger.debug("Encountered null metric context likely due to exception in preInvocation");
        }
    }

    static BooleanSupplier getEnabledSupplier(String component) {
        return InstrumentationProperties.getSystemPropertySupplier(component);
    }

}
