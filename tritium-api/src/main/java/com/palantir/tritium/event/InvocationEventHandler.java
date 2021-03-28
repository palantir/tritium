/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Interface for handing invocation events.
 * Backward-compatibility bridge type, will be removed in future major version bump.
 *
 * @see java.lang.reflect.InvocationHandler
 * @param <C> invocation context
 * @deprecated use {@link com.palantir.tritium.v1.api.event.InvocationEventHandler}
 */
@Deprecated // remove post 1.0
public interface InvocationEventHandler<C extends com.palantir.tritium.event.InvocationContext> {

    /**
     * Returns true if this event handler instance is enabled, otherwise false.
     *
     * @return true if handler is enabled
     */
    boolean isEnabled();

    /**
     * Invoked before invoking the method on the instance.
     *
     * @param instance the instance that the method was invoked on.
     * @param method the {@code Method} corresponding to the interface method invoked on the instance.
     * @param args an array of objects containing the values of the arguments passed in the method invocation on the
     *     instance, or empty array if interface method takes no arguments. Arguments of primitive types are wrapped in
     *     instances of the appropriate primitive wrapper class, such as {@code java.lang.Integer} or
     *     {@code java.lang.Boolean}.
     * @return the current invocation context. Null values are not recommended but are supported
     */
    C preInvocation(@Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args);

    /**
     * Invoked with the result of the invocation when it is successful.
     *
     * @param context the current invocation context or null if preInvocation returned null, or threw an exception.
     * @param result the return value from invocation, or null if {@link Void}.
     */
    void onSuccess(@Nullable com.palantir.tritium.event.InvocationContext context, @Nullable Object result);

    /**
     * Invoked when an invocation fails.
     *
     * <p>If the invocation throws an {@link java.lang.reflect.InvocationTargetException}, then the cause is passed to
     * this method. Any other thrown object is passed unaltered.
     *
     * @param context the current invocation context or null if preInvocation returned null, or threw an exception.
     * @param cause the throwable which caused the failure.
     */
    void onFailure(@Nullable com.palantir.tritium.event.InvocationContext context, @Nonnull Throwable cause);

    /**
     * Adapts to a {@link com.palantir.tritium.v1.api.event.InvocationEventHandler} v1 compatible handler.
     * @deprecated use {@link com.palantir.tritium.v1.api.event.InvocationEventHandler}
     */
    @Deprecated // remove post 1.0
    default com.palantir.tritium.v1.api.event.InvocationEventHandler<
                    com.palantir.tritium.v1.api.event.InvocationContext>
            asV1() {
        return new com.palantir.tritium.v1.api.event.InvocationEventHandler<
                com.palantir.tritium.v1.api.event.InvocationContext>() {
            @Override
            public boolean isEnabled() {
                return InvocationEventHandler.this.isEnabled();
            }

            @Override
            public com.palantir.tritium.v1.api.event.InvocationContext preInvocation(
                    @Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
                return InvocationEventHandler.this.preInvocation(instance, method, args);
            }

            @Override
            public void onSuccess(
                    @Nullable com.palantir.tritium.v1.api.event.InvocationContext context, @Nullable Object result) {
                InvocationEventHandler.this.onSuccess((com.palantir.tritium.event.InvocationContext) context, result);
            }

            @Override
            public void onFailure(
                    @Nullable com.palantir.tritium.v1.api.event.InvocationContext context, @Nonnull Throwable cause) {
                InvocationEventHandler.this.onFailure((com.palantir.tritium.event.InvocationContext) context, cause);
            }
        };
    }
}
