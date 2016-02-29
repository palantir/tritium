/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

/**
 * Abstract invocation event handler implementation.
 *
 * @param <C>
 *        invocation context
 */
public abstract class AbstractInvocationEventHandler<C extends InvocationContext>
        implements InvocationEventHandler<C> {

    private static final String INSTRUMENT_PREFIX = "instrument";
    private static final Object[] NO_ARGS = {};

    private final BooleanSupplier isEnabledSupplier;

    /**
     * Always enabled instrumentation handler.
     */
    protected AbstractInvocationEventHandler() {
        this(() -> true);
    }

    protected AbstractInvocationEventHandler(BooleanSupplier isEnabledSupplier) {
        this.isEnabledSupplier = checkNotNull(isEnabledSupplier, "isEnabledSupplier");
    }

    /**
     * Returns true if instrumentation handling is enabled, otherwise false.
     *
     * @return whether instrumentation handling is enabled
     */
    @Override
    public final boolean isEnabled() {
        return isEnabledSupplier.getAsBoolean();
    }

    /**
     * @param clazz
     *        instrumentation handler class
     * @return false if "instrument.fully.qualified.class.Name" is set to "false", otherwise true
     */
    protected static BooleanSupplier getSystemPropertySupplier(
            Class<? extends InvocationEventHandler<InvocationContext>> clazz) {

        checkNotNull(clazz, "clazz");
        return getSystemPropertySupplier(clazz.getName());
    }

    protected static BooleanSupplier getSystemPropertySupplier(String name) {
        checkArgument(!Strings.isNullOrEmpty(name), "name cannot be null or empty, was '%s'", name);
        boolean instrumentationEnabled = !"false".equalsIgnoreCase(System.getProperty(INSTRUMENT_PREFIX))
                && Boolean.parseBoolean(System.getProperty(INSTRUMENT_PREFIX + "." + name, "true"));
        return () -> instrumentationEnabled;
    }

    public static Object[] nullToEmpty(@Nullable Object[] args) {
        return (args == null) ? NO_ARGS : args;
    }

}
