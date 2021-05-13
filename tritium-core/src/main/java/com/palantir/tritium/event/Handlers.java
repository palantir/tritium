/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.tritium.api.event.InstrumentationFilter;
import java.lang.reflect.Method;
import java.util.Objects;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Handlers {

    private static final Logger log = LoggerFactory.getLogger(Handlers.class);

    /**
     * The caller is expected to check {@link InvocationEventHandler#isEnabled()} prior to calling this method,
     * allowing argument array allocation to be avoided when the handler is not enabled.
     */
    @Nullable
    public static InvocationContext pre(
            InvocationEventHandler<?> handler,
            InstrumentationFilter filter,
            Object instance,
            Method method,
            Object[] args) {
        try {
            return filter.shouldInstrument(instance, method, args)
                    ? handler.preInvocation(instance, method, args)
                    : DisabledHandlerSentinel.INSTANCE;
        } catch (Throwable t) {
            logPreInvocationFailure(handler, instance, method, t);
            return null;
        }
    }

    /**
     * Identical to {@link #pre(InvocationEventHandler, InstrumentationFilter, Object, Method, Object[])}
     * except that {@link InvocationEventHandler#isEnabled()} is checked along with
     * {@link InstrumentationFilter#shouldInstrument(Object, Method, Object[])}. This should be used when
     * argument array allocation has already occurred and cannot be avoided.
     */
    @Nullable
    public static InvocationContext preWithEnabledCheck(
            InvocationEventHandler<?> handler,
            InstrumentationFilter filter,
            Object instance,
            Method method,
            Object[] args) {
        try {
            return handler.isEnabled() && filter.shouldInstrument(instance, method, args)
                    ? handler.preInvocation(instance, method, args)
                    : DisabledHandlerSentinel.INSTANCE;
        } catch (Throwable t) {
            logPreInvocationFailure(handler, instance, method, t);
            return null;
        }
    }

    private static void logPreInvocationFailure(
            InvocationEventHandler<? extends InvocationContext> handler,
            Object instance,
            Method method,
            Throwable throwable) {
        if (log.isWarnEnabled()) {
            log.warn(
                    "Exception handling preInvocation({}): invocation of {}.{} on {} threw",
                    UnsafeArg.of("handler", handler),
                    SafeArg.of("class", method.getDeclaringClass().getCanonicalName()),
                    SafeArg.of("method", method.getName()),
                    UnsafeArg.of("instance", Objects.toString(instance)),
                    throwable);
        }
    }

    public static void onSuccess(InvocationEventHandler<?> handler, @Nullable InvocationContext context) {
        onSuccess(handler, context, null);
    }

    public static void onSuccess(
            InvocationEventHandler<?> handler, @Nullable InvocationContext context, @Nullable Object result) {
        if (context != DisabledHandlerSentinel.INSTANCE) {
            try {
                handler.onSuccess(context, result);
            } catch (Throwable t) {
                logOnSuccessFailure(handler, context, result, t);
            }
        }
    }

    private static void logOnSuccessFailure(
            InvocationEventHandler<?> handler,
            @Nullable InvocationContext context,
            @Nullable Object result,
            Throwable throwable) {
        if (log.isWarnEnabled()) {
            log.warn(
                    "Exception {}.onSuccess({}, {})",
                    UnsafeArg.of("handler", handler),
                    UnsafeArg.of("context", context),
                    SafeArg.of(
                            "result",
                            result == null ? "null" : result.getClass().getSimpleName()),
                    throwable);
        }
    }

    public static void onFailure(
            InvocationEventHandler<?> handler, @Nullable InvocationContext context, Throwable thrown) {
        if (context != DisabledHandlerSentinel.INSTANCE) {
            try {
                handler.onFailure(context, thrown);
            } catch (Throwable t) {
                logOnFailureFailure(handler, context, thrown, t);
            }
        }
    }

    private static void logOnFailureFailure(
            InvocationEventHandler<?> handler,
            @Nullable InvocationContext context,
            Throwable thrown,
            Throwable throwable) {
        if (log.isWarnEnabled()) {
            log.warn(
                    "Exception {}.onFailure({}, {})",
                    UnsafeArg.of("handler", handler),
                    UnsafeArg.of("context", context),
                    SafeArg.of(
                            "result",
                            thrown == null ? "null" : thrown.getClass().getSimpleName()),
                    throwable);
        }
    }

    private Handlers() {}

    // A sentinel value is used to differentiate null contexts returned by handlers from
    // invocations on disabled handlers.
    private enum DisabledHandlerSentinel implements InvocationContext {
        INSTANCE;

        @Override
        public long getStartTimeNanos() {
            throw fail();
        }

        @Override
        public Object getInstance() {
            throw fail();
        }

        @Override
        public Method getMethod() {
            throw fail();
        }

        @Override
        public Object[] getArgs() {
            throw fail();
        }

        private static RuntimeException fail() {
            throw new UnsupportedOperationException("methods should not be invoked");
        }
    }
}
