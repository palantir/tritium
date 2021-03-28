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

package com.palantir.tritium.event.log;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/**
 * {@link com.palantir.tritium.event.InvocationEventHandler} that times every method invocation
 * and logs to specified logger.
 * @deprecated use {@link com.palantir.tritium.v1.slf4j.event.LoggingInvocationEventHandler}
 */
@Deprecated // remove post 1.0
@SuppressWarnings("UnnecessarilyFullyQualified") // deprecated types
public class LoggingInvocationEventHandler
        extends com.palantir.tritium.event.AbstractInvocationEventHandler<
                com.palantir.tritium.event.InvocationContext> {

    @SuppressWarnings({"deprecation", "UnnecessarilyFullyQualified"}) // back-compatibility return type for now
    public static final com.palantir.tritium.api.functions.LongPredicate LOG_ALL_DURATIONS = _input -> true;

    @SuppressWarnings({"deprecation", "UnnecessarilyFullyQualified"}) // back-compatibility return type for now
    public static final com.palantir.tritium.api.functions.LongPredicate LOG_DURATIONS_GREATER_THAN_1_MICROSECOND =
            nanos -> TimeUnit.MICROSECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 1;

    @SuppressWarnings({"deprecation", "UnnecessarilyFullyQualified"}) // back-compatibility return type for now
    public static final com.palantir.tritium.api.functions.LongPredicate LOG_DURATIONS_GREATER_THAN_0_MILLIS =
            nanos -> TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 0;

    @SuppressWarnings({"deprecation", "UnnecessarilyFullyQualified"}) // back-compat return type for now
    public static final com.palantir.tritium.api.functions.LongPredicate NEVER_LOG = _input -> false;

    private final com.palantir.tritium.v1.slf4j.event.LoggingInvocationEventHandler delegate;

    /**
     * Bridge for backward compatibility.
     * @deprecated use {@link com.palantir.tritium.v1.slf4j.event.LoggingInvocationEventHandler}
     */
    @Deprecated // remove post 1.0
    public LoggingInvocationEventHandler(Logger logger, LoggingLevel level) {
        this(logger, level, (java.util.function.LongPredicate) LOG_ALL_DURATIONS);
    }

    /**
     * Bridge for backward compatibility.
     *
     * @deprecated use {@link #LoggingInvocationEventHandler(Logger, LoggingLevel, java.util.function.LongPredicate)}
     */
    @Deprecated // remove post 1.0
    @SuppressWarnings("FunctionalInterfaceClash") // backward compatibility
    public LoggingInvocationEventHandler(
            Logger logger, LoggingLevel level, com.palantir.tritium.api.functions.LongPredicate durationPredicate) {
        this(logger, level, (java.util.function.LongPredicate) durationPredicate);
    }

    @SuppressWarnings("FunctionalInterfaceClash") // backward compatibility
    public LoggingInvocationEventHandler(
            Logger logger, LoggingLevel level, java.util.function.LongPredicate durationPredicate) {
        super(com.palantir.tritium.v1.slf4j.event.LoggingInvocationEventHandler.createEnabledSupplier(
                logger, level.asV1()));
        this.delegate = new com.palantir.tritium.v1.slf4j.event.LoggingInvocationEventHandler(
                logger, level.asV1(), durationPredicate);
    }

    @Override
    public final com.palantir.tritium.event.InvocationContext preInvocation(
            @Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        return com.palantir.tritium.event.DefaultInvocationContext.wrap(delegate.preInvocation(instance, method, args));
    }

    @Override
    public final void onSuccess(
            @Nullable com.palantir.tritium.v1.api.event.InvocationContext context, @Nullable Object result) {
        delegate.onSuccess(context, result);
    }

    @Override
    public final void onFailure(
            @Nullable com.palantir.tritium.v1.api.event.InvocationContext context, @Nonnull Throwable cause) {
        delegate.onFailure(context, cause);
    }
}
