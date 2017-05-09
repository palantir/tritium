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

package com.palantir.tritium.event;

import static com.google.common.base.Preconditions.checkNotNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.annotation.Nullable;

public class DefaultInvocationContext implements InvocationContext {

    private static final Object[] NO_ARGS = {};

    private final long startTimeNanos;
    private final Object instance;
    private final Method method;
    private final Object[] args;

    protected DefaultInvocationContext(long startTimeNanos, Object instance, Method method, @Nullable Object[] args) {
        this.startTimeNanos = startTimeNanos;
        this.instance = instance;
        this.method = method;
        this.args = toNonNullClone(args);
    }

    private static Object[] toNonNullClone(@Nullable Object[] args) {
        return args == null ? NO_ARGS : args.clone();
    }

    public static InvocationContext of(Object instance, Method method, @Nullable Object[] args) {
        return new DefaultInvocationContext(
                System.nanoTime(),
                checkNotNull(instance, "instance"),
                checkNotNull(method, "method"),
                args);
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
    public String toString() {
        return "DefaultInvocationContext [startTimeNanos=" + startTimeNanos + ", instance=" + instance + ", method="
                + method + ", args=" + Arrays.toString(args) + "]";
    }

}
