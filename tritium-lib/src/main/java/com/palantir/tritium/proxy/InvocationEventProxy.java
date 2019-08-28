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

package com.palantir.tritium.proxy;

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.CompositeInvocationEventHandler;
import com.palantir.tritium.event.InstrumentationFilters;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class InvocationEventProxy implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(InvocationEventProxy.class);
    private static final Object[] EMPTY_ARRAY = new Object[0];

    private final InstrumentationFilter filter;
    private final InvocationEventHandler<?> eventHandler;

    /**
     * Always enabled instrumentation handler.
     *
     * @param handlers event handlers
     */
    protected InvocationEventProxy(List<InvocationEventHandler<InvocationContext>> handlers) {
        this(handlers, InstrumentationFilters.INSTRUMENT_ALL);
    }

    protected InvocationEventProxy(List<InvocationEventHandler<InvocationContext>> handlers,
                                   InstrumentationFilter filter) {
        checkNotNull(filter, "filter");
        checkNotNull(handlers, "handlers");
        this.eventHandler = CompositeInvocationEventHandler.of(handlers);
        this.filter = filter;
    }

    /**
     * Returns the proxy delegate.
     *
     * @return delegate
     */
    abstract Object getDelegate();

    @Override
    public String toString() {
        return String.valueOf(getDelegate());
    }

    /**
     * Returns true if instrumentation handling is enabled, otherwise false.
     *
     * @return whether instrumentation handling is enabled
     */
    private boolean isEnabled(Object instance, Method method, Object[] args) {
        try {
            return eventHandler.isEnabled()
                    && filter.shouldInstrument(instance, method, args);
        } catch (Throwable t) {
            logInvocationWarning("isEnabled", instance, method, t);
            return false;
        }
    }

    /** Optimized to avoid excessive stack frames for readable stack traces. */
    @Override
    @Nullable
    public final Object invoke(Object proxy, Method method, @Nullable Object[] nullableArgs) throws Throwable {
        Object[] arguments = nullableArgs == null ? EMPTY_ARRAY : nullableArgs;
        if (isSpecialMethod(method, arguments)) {
            return handleSpecialMethod(proxy, method, arguments);
        }
        if (isEnabled(proxy, method, arguments)) {
            InvocationContext context = handlePreInvocation(proxy, method, arguments);
            try {
                Object result = method.invoke(getDelegate(), arguments);
                return handleOnSuccess(context, result);
            } catch (Throwable t) {
                throw invocationFailed(context, t);
            }
        } else {
            try {
                return method.invoke(getDelegate(), arguments);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static boolean isSpecialMethod(Method method, Object[] arguments) {
        return isHashCode(method, arguments) || isEquals(method, arguments) || isToString(method, arguments);
    }

    private Object handleSpecialMethod(Object proxy, Method method, Object[] arguments) {
        if (isHashCode(method, arguments)) {
            return hashCode();
        }
        if (isEquals(method, arguments)) {
            Object arg = arguments[0];
            return arg != null && proxy == arg;
        }
        if (isToString(method, arguments)) {
            return toString();
        }
        throw new SafeIllegalStateException("Method does not require special handling",
                SafeArg.of("method", method.toString()));
    }

    private static boolean isEquals(Method method, Object[] arguments) {
        return arguments.length == 1
                && "equals".equals(method.getName())
                && method.getParameterTypes()[0] == Object.class;
    }

    private static boolean isHashCode(Method method, Object[] arguments) {
        return arguments.length == 0 && "hashCode".equals(method.getName());
    }

    private static boolean isToString(Method method, Object[] arguments) {
        return arguments.length == 0 && "toString".equals(method.getName());
    }

    @Nullable
    @VisibleForTesting
    final InvocationContext handlePreInvocation(Object instance, Method method, Object[] args) {
        try {
            return eventHandler.preInvocation(instance, method, args);
        } catch (RuntimeException e) {
            logInvocationWarning("preInvocation", instance, method, e);
        }
        return null;
    }

    @Nullable
    @VisibleForTesting
    final Object handleOnSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        try {
            eventHandler.onSuccess(context, result);
        } catch (RuntimeException e) {
            logInvocationWarningOnSuccess(context, result, e);
        }
        return result;
    }

    private Throwable invocationFailed(@Nullable InvocationContext context, Throwable cause) {
        if (cause instanceof InvocationTargetException) {
            return handleOnFailure(context, cause.getCause());
        }
        return handleOnFailure(context, cause);
    }

    final Throwable handleOnFailure(@Nullable InvocationContext context, Throwable cause) {
        try {
            eventHandler.onFailure(context, cause);
        } catch (RuntimeException e) {
            logInvocationWarningOnFailure(context, cause, e);
        }
        return cause;
    }

    private static void logInvocationWarningOnSuccess(
            @Nullable InvocationContext context,
            @Nullable Object result,
            Exception cause) {
        logInvocationWarning("onSuccess", context, result, cause);
    }

    private static void logInvocationWarningOnFailure(
            @Nullable InvocationContext context,
            @Nullable Throwable result,
            Exception cause) {
        logInvocationWarning("onFailure", context, result, cause);
    }

    private static SafeArg<String> safeSimpleClassName(@CompileTimeConstant String name, @Nullable Object object) {
        return SafeArg.of(name, (object == null) ? "null" : object.getClass().getSimpleName());
    }

    static void logInvocationWarning(
            String event,
            @Nullable InvocationContext context,
            @Nullable Object result,
            Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("{} occurred handling '{}' ({}, {}): {}",
                    safeSimpleClassName("cause", cause),
                    SafeArg.of("event", event),
                    UnsafeArg.of("context", context),
                    safeSimpleClassName("result", result),
                    cause);
        }
    }

    static void logInvocationWarning(
            String event,
            Object instance,
            Method method,
            Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("{} occurred handling '{}' invocation of {} {} on {} instance: {}",
                    safeSimpleClassName("cause", cause),
                    SafeArg.of("event", event),
                    SafeArg.of("class", method.getDeclaringClass().getName()),
                    SafeArg.of("method", method),
                    safeSimpleClassName("instanceClass", instance),
                    UnsafeArg.of("instance", instance),
                    cause);
        }
    }

}
