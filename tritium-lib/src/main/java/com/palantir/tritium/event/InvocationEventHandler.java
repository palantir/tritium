/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event;

import java.lang.reflect.Method;
import javax.annotation.Nullable;

/**
 * Interface for handing invocation events.
 *
 * @param <C>
 *        invocation context
 */
public interface InvocationEventHandler<C extends InvocationContext> {

    /**
     * Returns true if this event handler instance is enabled, otherwise false.
     *
     * @return true if handler is enabled
     */
    boolean isEnabled();

    /**
     * Invoked before invoking the method on the instance.
     */
    InvocationContext preInvocation(Object instance, Method method, Object[] args);

    /**
     * Invoked with the result of the invocation when it is successful.
     */
    void onSuccess(@Nullable InvocationContext context, @Nullable Object result);

    /**
     * Invoked when an invocation fails.
     *
     * <p>
     * If the invocation throws an {@link java.lang.reflect.InvocationTargetException}, then the cause is passed to this
     * method. Any other thrown object is passed unaltered.
     */
    void onFailure(@Nullable InvocationContext context, Throwable cause);

}
