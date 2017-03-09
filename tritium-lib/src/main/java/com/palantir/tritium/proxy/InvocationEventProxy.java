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

package com.palantir.tritium.proxy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.reflect.AbstractInvocationHandler;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.CompositeInvocationEventHandler;
import com.palantir.tritium.event.InstrumentationFilter;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(InvocationEventProxy.class);
    private static final Object[] NO_ARGS = {};

    private final InstrumentationFilter filter;
    private final InvocationEventHandler<?> eventHandler;

    /**
     * Always enabled instrumentation handler.
     * @param handlers event handlers
     */
    protected InvocationEventProxy(List<InvocationEventHandler<InvocationContext>> handlers) {
        this(InstrumentationFilters.INSTRUMENT_ALL, handlers);
    }

    protected InvocationEventProxy(BooleanSupplier isEnabledSupplier,
            List<InvocationEventHandler<InvocationContext>> handlers) {
        this(InstrumentationFilters.from(isEnabledSupplier), handlers);
    }

    protected InvocationEventProxy(InstrumentationFilter filter,
            List<InvocationEventHandler<InvocationContext>> handlers) {
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
            LOGGER.warn("Exception handling preInvocation({}): {}",
                    toInvocationDebugString(instance, method, args), t.toString(), t);
            return false;
        }
    }

    @Override
    @Nullable
    @SuppressWarnings("checkstyle:IllegalThrows")
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
     *
     * <p>
     * Unlike {@link #invoke}, {@code args} will never be null. When the method has no parameter, an empty array is
     * passed in.
     */
    @Nullable
    @SuppressWarnings("checkstyle:IllegalThrows")
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
            LOGGER.warn("Exception handling preInvocation({}): {}",
                    toInvocationDebugString(instance, method, args), e.toString(), e);
        }
        return null;
    }

    @Nullable
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
            LOGGER.warn("Exception handling onSuccess({}, {}): {}",
                    context, result, e.toString(), e);
        }
        return result;
    }

    final Throwable handleOnFailure(@Nullable InvocationContext context, Throwable cause) {
        try {
            eventHandler.onFailure(context, cause);
        } catch (RuntimeException e) {
            LOGGER.warn("Exception handling onFailure({}, {}): {}",
                    context, cause, e.toString(), e);
        }
        return cause;
    }

    protected final String toInvocationDebugString(Object instance, Method method, @Nullable Object[] args) {
        return "invocation of "
                + method.getDeclaringClass() + '.' + method.getName()
                + " with arguments " + Arrays.toString(nullToEmpty(args))
                + " on " + instance + " via " + this + " : ";
    }

    protected static Object[] nullToEmpty(@Nullable Object[] args) {
        return (args == null) ? NO_ARGS : args;
    }

}
