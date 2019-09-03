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

    @Nullable
    private InvocationEventHandler<InvocationContext> tryGetEnabledHandler(int index) {
        InvocationEventHandler<InvocationContext> handler = handlers.get(index);
        if (handler != null && handler.isEnabled()) {
            return handler;
        }
        return null;
    }

    @Override
    public InvocationContext preInvocation(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        final int count = handlers.size();
        InvocationContext[] contexts = new InvocationContext[count];

        for (int i = 0; i < count; i++) {
            contexts[i] = handlePreInvocation(tryGetEnabledHandler(i), instance, method, args);
        }

        return new CompositeInvocationContext(instance, method, args, contexts);
    }

    @Override
    public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        debugIfNullContext(context);
        if (context != null) {
            success(((CompositeInvocationContext) context).getContexts(), result);
        }
    }

    private void success(@Nonnull InvocationContext[] contexts, @Nullable Object result) {
        for (int i = contexts.length - 1; i > -1; i--) {
            InvocationContext context = contexts[i];
            if (context != null) {
                handleSuccess(handlers.get(i), context, result);
            }
        }
    }

    @Override
    public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        debugIfNullContext(context);
        if (context != null) {
            failure(((CompositeInvocationContext) context).getContexts(), cause);
        }
    }

    private void failure(InvocationContext[] contexts, @Nonnull Throwable cause) {
        for (int i = contexts.length - 1; i > -1; i--) {
            InvocationContext context = contexts[i];
            if (context != null) {
                handleFailure(handlers.get(i), context, cause);
            }
        }
    }

    @Nullable
    private static InvocationContext handlePreInvocation(
            @Nullable InvocationEventHandler<? extends InvocationContext> handler,
            Object instance,
            Method method,
            Object[] args) {
        try {
            if (handler != null) {
                return handler.preInvocation(instance, method, args);
            }
        } catch (RuntimeException e) {
            preInvocationFailed(handler, instance, method, e);
        }
        return null;
    }

    @Override
    public String toString() {
        return "CompositeInvocationEventHandler{" + "handlers=" + handlers + '}';
    }

    private static void preInvocationFailed(
            @Nullable InvocationEventHandler<? extends InvocationContext> handler,
            @Nullable Object instance,
            Method method,
            @Nullable Exception exception) {
        logger.warn(
                "Exception handling preInvocation({}): invocation of {}.{} on {} threw",
                UnsafeArg.of("handler", handler),
                SafeArg.of("class", method.getDeclaringClass().getCanonicalName()),
                SafeArg.of("method", method.getName()),
                UnsafeArg.of("instance", instance),
                exception);
    }

    private static void handleSuccess(
            @Nullable InvocationEventHandler<?> handler,
            @Nullable InvocationContext context,
            @Nullable Object result) {
        try {
            if (handler != null) {
                handler.onSuccess(context, result);
            }
        } catch (RuntimeException exception) {
            eventFailed("onSuccess", context, result, exception);
        }
    }

    private static void handleFailure(
            @Nullable InvocationEventHandler<?> handler,
            @Nullable InvocationContext context,
            Throwable cause) {
        try {
            if (handler != null) {
                handler.onFailure(context, cause);
            }
        } catch (RuntimeException exception) {
            eventFailed("onFailure", context, cause, exception);
        }
    }

    private static void eventFailed(
            String event,
            @Nullable InvocationContext context,
            @Nullable Object result,
            RuntimeException exception) {
        logger.warn(
                "Exception handling {}({}, {})",
                SafeArg.of("event", event),
                UnsafeArg.of("context", context),
                UnsafeArg.of("result", result),
                exception);
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

        InvocationContext[] getContexts() {
            return contexts;
        }
    }
}
