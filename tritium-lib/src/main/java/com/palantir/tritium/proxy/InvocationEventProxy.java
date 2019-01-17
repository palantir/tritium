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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.reflect.AbstractInvocationHandler;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.CompositeInvocationEventHandler;
import com.palantir.tritium.event.InstrumentationFilters;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class InvocationEventProxy<C extends InvocationContext>
        extends AbstractInvocationHandler implements InvocationHandler {

    private static final Logger logger = LoggerFactory.getLogger(InvocationEventProxy.class);
    private static final Object[] NO_ARGS = {};

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
            logInvocationWarning("isEnabled", instance, method, args, t);
            return false;
        }
    }


    @Override
    @Nullable
    @SuppressWarnings("checkstyle:illegalthrows")
    protected final Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {
        if (isEnabled(proxy, method, args)) {
            return instrumentInvocation(proxy, method, args);
        } else {
            return execute(method, args);
        }
    }

    /**
     * {@link #invoke} delegates to this method upon any method invocation on the instance, except
     * {@link Object#equals}, {@link Object#hashCode} and {@link Object#toString}. The result will be returned as the
     * proxied method's return value.
     * <p>
     * <p>
     * Unlike {@link #invoke}, {@code args} will never be null. When the method has no parameter, an empty array is
     * passed in.
     */
    @Nullable
    @SuppressWarnings("checkstyle:illegalthrows")
    public final Object instrumentInvocation(Object instance, Method method, Object[] args) throws Throwable {
        InvocationContext context = handlePreInvocation(instance, method, args);
        try {
            Object result = execute(method, args);
            return handleOnSuccess(context, result);
        } catch (InvocationTargetException e) {
            throw handleOnFailure(context, e.getCause());
        } catch (Throwable t) {
            throw handleOnFailure(context, t);
        }
    }

    @Nullable
    final InvocationContext handlePreInvocation(Object instance, Method method, Object[] args) {
        try {
            return eventHandler.preInvocation(instance, method, args);
        } catch (RuntimeException e) {
            logInvocationWarning("preInvocation", instance, method, args, e);
        }
        return null;
    }

    @Nullable
    @SuppressWarnings("checkstyle:illegalthrows")
    final Object execute(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(getDelegate(), args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Nullable
    final Object handleOnSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        try {
            eventHandler.onSuccess(context, result);
        } catch (RuntimeException e) {
            logInvocationWarningOnSuccess(context, result, e);
        }
        return result;
    }

    private static void logInvocationWarningOnSuccess(@Nullable InvocationContext context, @Nullable Object result,
            Exception cause) {
        logger.warn("{} exception handling onSuccess({}, {}): {}",
                safeSimpleClassName("cause", cause), context, safeSimpleClassName("result", result), cause);
    }

    final Throwable handleOnFailure(@Nullable InvocationContext context, Throwable cause) {
        try {
            eventHandler.onFailure(context, cause);
        } catch (RuntimeException e) {
            logInvocationWarningOnFailure(context, cause, e);
        }
        return cause;
    }

    private static void logInvocationWarningOnFailure(@Nullable InvocationContext context, @Nullable Throwable result,
            Exception cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("{} exception handling onFailure({}, {}): {}",
                    safeSimpleClassName("cause", cause),
                    UnsafeArg.of("context", context),
                    safeSimpleClassName("result", result),
                    cause);
        }
    }

    private static SafeArg<String> safeSimpleClassName(@CompileTimeConstant String name, @Nullable Object object) {
        return SafeArg.of(name, (object == null) ? "null" : object.getClass().getSimpleName());
    }

    static void logInvocationWarning(String event, Object instance, Method method, Object[] args,
            Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("{} exception handling {} invocation of {}.{} with arguments {} on {}",
                    safeSimpleClassName("throwable", cause),
                    SafeArg.of("event", event),
                    SafeArg.of("class", method.getDeclaringClass()),
                    SafeArg.of("method", method.getName()),
                    unsafeArgs(args),
                    UnsafeArg.of("instance", instance),
                    cause);
        }
    }

    private static UnsafeArg<List<Object>> unsafeArgs(Object[] args) {
        return UnsafeArg.of("args", Arrays.asList(nullToEmpty(args)));
    }

    private static Object[] nullToEmpty(@Nullable Object[] args) {
        return (args == null) ? NO_ARGS : args;
    }

}
