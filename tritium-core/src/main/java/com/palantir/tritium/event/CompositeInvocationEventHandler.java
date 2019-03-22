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

package com.palantir.tritium.event;

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.lang.reflect.Method;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompositeInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger logger = LoggerFactory.getLogger(CompositeInvocationEventHandler.class);

    private final List<InvocationEventHandler<InvocationContext>> handlers;

    private CompositeInvocationEventHandler(List<InvocationEventHandler<InvocationContext>> handlers) {
        this.handlers = ImmutableList.copyOf(handlers);
    }

    public static InvocationEventHandler<InvocationContext> of(
            List<InvocationEventHandler<InvocationContext>> handlers) {

        if (handlers.isEmpty()) {
            return NoOpInvocationEventHandler.INSTANCE;
        } else if (handlers.size() == 1) {
            return checkNotNull(handlers.iterator().next(), "at index 0");
        } else {
            return new CompositeInvocationEventHandler(handlers);
        }
    }

    @Override
    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        InvocationContext[] contexts = new InvocationContext[handlers.size()];

        for (int i = 0; i < handlers.size(); i++) {
            InvocationEventHandler<InvocationContext> handler = handlers.get(i);
            if (handler.isEnabled()) {
                contexts[i] = handlePreInvocation(handler, instance, method, args);
            }
        }

        return new CompositeInvocationContext(instance, method, args, contexts);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext invocationContext, @Nullable Object result) {
        if (invocationContext instanceof CompositeInvocationContext) {
            CompositeInvocationContext compositeContext = (CompositeInvocationContext) invocationContext;
            InvocationContext[] contexts = compositeContext.getContexts();

            for (int i = contexts.length - 1; i >= 0; i--) {
                InvocationEventHandler<InvocationContext> handler = handlers.get(i);
                if (handler.isEnabled()) {
                    handleSuccess(handler, contexts[i], result);
                }
            }
        } else {
            logger.debug("onSuccess InvocationContext was not a CompositeInvocationContext: {}", invocationContext);
        }
    }

    @Override
    public void onFailure(@Nullable InvocationContext invocationContext, @Nonnull Throwable cause) {
        if (invocationContext instanceof CompositeInvocationContext) {
            CompositeInvocationContext compositeContext = (CompositeInvocationContext) invocationContext;
            InvocationContext[] contexts = compositeContext.getContexts();

            for (int i = contexts.length - 1; i >= 0; i--) {
                InvocationEventHandler<InvocationContext> handler = handlers.get(i);
                if (handler.isEnabled()) {
                    handleFailure(handler, contexts[i], cause);
                }
            }
        } else {
            logger.debug("onFailure InvocationContext was not a CompositeInvocationContext: {}", invocationContext);
        }
    }

    @Nullable
    private static InvocationContext handlePreInvocation(InvocationEventHandler<? extends InvocationContext> handler,
            Object instance,
            Method method,
            Object[] args) {

        try {
            return handler.preInvocation(instance, method, args);
        } catch (RuntimeException e) {
            preInvocationFailed(handler, instance, method, e);
            return null;
        }
    }

    @Override
    public String toString() {
        return "CompositeInvocationEventHandler{" + "handlers=" + handlers + '}';
    }

    private static void preInvocationFailed(
            InvocationEventHandler<? extends InvocationContext> handler,
            Object instance,
            Method method,
            Exception exception) {
        logger.warn(
                "Exception handling preInvocation({}): invocation of {}.{} on {} threw",
                UnsafeArg.of("handler", handler),
                SafeArg.of("class", method.getDeclaringClass().getCanonicalName()),
                SafeArg.of("method", method.getName()),
                UnsafeArg.of("instance", instance),
                exception);
    }

    private static void handleSuccess(InvocationEventHandler<?> handler,
            @Nullable InvocationContext context,
            @Nullable Object result) {

        try {
            handler.onSuccess(context, result);
        } catch (RuntimeException exception) {
            logger.warn("Exception handling onSuccess({}, {})",
                    UnsafeArg.of("context", context),
                    UnsafeArg.of("result", result),
                    exception);
        }
    }

    private static void handleFailure(InvocationEventHandler<?> handler,
            @Nullable InvocationContext context,
            Throwable cause) {

        try {
            handler.onFailure(context, cause);
        } catch (RuntimeException exception) {
            logger.warn("Exception handling onFailure({}, {})",
                    UnsafeArg.of("context", context),
                    UnsafeArg.of("cause", cause),
                    exception);
        }
    }

    static class CompositeInvocationContext extends DefaultInvocationContext {

        private final InvocationContext[] contexts;

        CompositeInvocationContext(
                Object instance,
                Method method,
                @Nullable Object[] args,
                InvocationContext[] contexts) {

            super(System.nanoTime(), instance, method, args);
            this.contexts = checkNotNull(contexts);
        }

        public InvocationContext[] getContexts() {
            return contexts;
        }
    }
}
