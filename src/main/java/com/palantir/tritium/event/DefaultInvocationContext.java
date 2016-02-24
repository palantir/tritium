/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event;

import static com.google.common.base.Preconditions.checkNotNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.annotation.Nullable;

public class DefaultInvocationContext implements InvocationContext {

    private final long startTimeNanos;
    private final Object instance;
    private final Method method;
    private final Object[] args;

    protected DefaultInvocationContext(long startTimeNanos, Object instance, Method method, @Nullable Object[] args) {
        this.startTimeNanos = startTimeNanos;
        this.instance = checkNotNull(instance);
        this.method = checkNotNull(method);
        this.args = AbstractInvocationEventHandler.nullToEmpty(args).clone();
    }

    public static InvocationContext of(Object instance, Method method, @Nullable Object[] args) {
        return new DefaultInvocationContext(System.nanoTime(), instance, method, args);
    }

    @Override
    public final long getStartTimeNanos() {
        return startTimeNanos;
    }

    @Override
    public final Object getInstance() {
        return instance;
    }

    @Override
    public final Method getMethod() {
        return method;
    }

    @Override
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public final Object[] getArgs() {
        return args;
    }

    @Override
    @SuppressWarnings("checkstyle:designforextension")
    public String toString() {
        return "DefaultInvocationContext [startTimeNanos=" + startTimeNanos + ", instance=" + instance + ", method="
                + method + ", args=" + Arrays.toString(args) + "]";
    }

}
