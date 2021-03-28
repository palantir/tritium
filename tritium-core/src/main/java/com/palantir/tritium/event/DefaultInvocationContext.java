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

import java.lang.reflect.Method;
import javax.annotation.Nullable;

/**
 * Default representation of invocation event state.
 * @deprecated use {@link com.palantir.tritium.v1.core.event.DefaultInvocationContext}
 */
@Deprecated // remove post 1.0
public class DefaultInvocationContext implements com.palantir.tritium.event.InvocationContext {

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

    public static com.palantir.tritium.event.InvocationContext of(
            Object instance, Method method, @Nullable Object[] args) {
        return new DefaultInvocationContext(
                System.nanoTime(), checkNotNull(instance, "instance"), checkNotNull(method, "method"), args);
    }

    @Override
    @SuppressWarnings("deprecation") // backward compatibility bridge
    public final long getStartTimeNanos() {
        return startTimeNanos;
    }

    @Override
    @SuppressWarnings("deprecation") // backward compatibility bridge
    public final Object getInstance() {
        return instance;
    }

    @Override
    @SuppressWarnings("deprecation") // backward compatibility bridge
    public final Method getMethod() {
        return method;
    }

    @Override
    @SuppressWarnings("deprecation") // backward compatibility bridge
    public final Object[] getArgs() {
        return args;
    }

    /**
     * Wraps {@link com.palantir.tritium.v1.api.event.InvocationContext} as a
     * {@link com.palantir.tritium.event.InvocationContext}.
     * @deprecated use {@link com.palantir.tritium.v1.api.event.InvocationContext}
     */
    @Deprecated // remove post 1.0
    public static com.palantir.tritium.event.InvocationContext wrap(
            com.palantir.tritium.v1.api.event.InvocationContext context) {
        return new com.palantir.tritium.event.InvocationContext() {
            @Override
            public long getStartTimeNanos() {
                return context.getStartTimeNanos();
            }

            @Nullable
            @Override
            public Object getInstance() {
                return context.getInstance();
            }

            @Override
            public Method getMethod() {
                return context.getMethod();
            }

            @Override
            public Object[] getArgs() {
                return context.getArgs();
            }
        };
    }

    @Override
    @SuppressWarnings("DesignForExtension")
    public String toString() {
        return "DefaultInvocationContext [startTimeNanos="
                + startTimeNanos
                + ", instance="
                + instance
                + ", method="
                + method
                + ']';
    }
}
