/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.proxy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.reflect.AbstractInvocationHandler;
import com.palantir.tritium.event.CompositeInvocationEventHandler;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class InvocationEventProxy<C extends InvocationContext>
        extends AbstractInvocationHandler implements InvocationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvocationEventProxy.class);
    private static final Object[] NO_ARGS = {};

    private final BooleanSupplier isEnabledSupplier;
    private final InvocationEventHandler<?> eventHandler;

    /**
     * Always enabled instrumentation handler.
     * @param handlers event handlers
     */
    protected InvocationEventProxy(List<InvocationEventHandler<InvocationContext>> handlers) {
        this(() -> true, handlers);
    }

    protected InvocationEventProxy(BooleanSupplier isEnabledSupplier,
            List<InvocationEventHandler<InvocationContext>> handlers) {
        checkNotNull(handlers, "handlers");
        this.isEnabledSupplier = checkNotNull(isEnabledSupplier, "isEnabledSupplier");
        this.eventHandler = CompositeInvocationEventHandler.of(handlers);
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
    protected final boolean isEnabled() {
        return isEnabledSupplier.getAsBoolean();
    }

    @Override
    @Nullable
    @SuppressWarnings("checkstyle:IllegalThrows")
    protected final Object handleInvocation(Object proxy, Method method, Object[] args) throws Throwable {
        if (isEnabled()) {
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
    final Object execute(Method method, Object[] args)
            throws IllegalAccessException, InvocationTargetException {
        return method.invoke(getDelegate(), args);
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
