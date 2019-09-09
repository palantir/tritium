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

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompositeInvocationEventHandler extends AbstractInvocationEventHandler<Object[]> {

    private static final Logger logger = LoggerFactory.getLogger(CompositeInvocationEventHandler.class);

    private final InvocationEventHandler<?>[] handlers;

    private CompositeInvocationEventHandler(List<InvocationEventHandler<?>> handlers) {
        this.handlers = checkNotNull(handlers, "handlers").toArray(new InvocationEventHandler[0]);
        for (InvocationEventHandler<?> handler : handlers) {
            checkNotNull(handler, "Null handlers are not allowed");
        }
    }

    public static InvocationEventHandler<?> of(List<InvocationEventHandler<?>> handlers) {
        if (handlers.isEmpty()) {
            return NoOpInvocationEventHandler.INSTANCE;
        } else if (handlers.size() == 1) {
            return checkNotNull(handlers.get(0), "Null handlers are not allowed");
        } else {
            return new CompositeInvocationEventHandler(handlers);
        }
    }

    @Nullable
    private InvocationEventHandler<?> tryGetEnabledHandler(int index) {
        InvocationEventHandler<?> handler = handlers[index];
        if (handler.isEnabled()) {
            return handler;
        }
        return null;
    }

    @Override
    public Object[] preInvocation(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        Object[] contexts = new Object[handlers.length];

        for (int i = 0; i < handlers.length; i++) {
            contexts[i] = handlePreInvocation(tryGetEnabledHandler(i), instance, method, args);
        }

        return contexts;
    }

    @Override
    public void onSuccess(@Nullable Object[] context, @Nullable Object result) {
        debugIfNullContext(context);
        if (context != null) {
            success(context, result);
        }
    }

    @SuppressWarnings("unchecked")
    private void success(@Nonnull Object[] contexts, @Nullable Object result) {
        for (int i = contexts.length - 1; i > -1; i--) {
            handleSuccess((InvocationEventHandler<Object>) handlers[i], contexts[i], result);
        }
    }

    @Override
    public void onFailure(@Nullable Object[] context, @Nonnull Throwable cause) {
        debugIfNullContext(context);
        if (context != null) {
            failure(context, cause);
        }
    }

    @SuppressWarnings("unchecked")
    private void failure(Object[] contexts, @Nonnull Throwable cause) {
        for (int i = contexts.length - 1; i > -1; i--) {
            handleFailure((InvocationEventHandler<Object>) handlers[i], contexts[i], cause);
        }
    }

    @Nullable
    private static Object handlePreInvocation(
            @Nullable InvocationEventHandler<?> handler,
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
        return "CompositeInvocationEventHandler{" + "handlers=" + Arrays.toString(handlers) + '}';
    }

    private static void preInvocationFailed(
            @Nullable InvocationEventHandler<?> handler,
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

    private static <T> void handleSuccess(
            InvocationEventHandler<T> handler,
            @Nullable T context,
            @Nullable Object result) {
        if (context != null) {
            try {
                handler.onSuccess(context, result);
            } catch (RuntimeException exception) {
                eventFailed("onSuccess", context, result, exception);
            }
        }
    }

    private static <T> void handleFailure(
            InvocationEventHandler<T> handler,
            @Nullable T context,
            Throwable cause) {
        if (context != null) {
            try {
                handler.onFailure(context, cause);
            } catch (RuntimeException exception) {
                eventFailed("onFailure", context, cause, exception);
            }
        }
    }

    private static void eventFailed(
            String event,
            @Nullable Object context,
            @Nullable Object result,
            RuntimeException exception) {
        logger.warn(
                "Exception handling {}({}, {})",
                SafeArg.of("event", event),
                UnsafeArg.of("context", context),
                UnsafeArg.of("result", result),
                exception);
    }
}
