/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event;

import java.lang.reflect.Method;

/**
 * Represents the state when an invocation event occurred.
 */
public interface InvocationContext {

    /**
     * Returns the invocation start time in nanoseconds local to this machine.
     *
     * @return start time
     */
    long getStartTimeNanos();

    /**
     * Returns the instance a method was invoked upon.
     *
     * @return instance invoked
     */
    Object getInstance();

    /**
     * Returns the method invoked.
     *
     * @return method invoked
     */
    Method getMethod();

    /**
     * Returns the array of arguments for the specified invocation.
     *
     * @return arguments
     */
    Object[] getArgs();

}
