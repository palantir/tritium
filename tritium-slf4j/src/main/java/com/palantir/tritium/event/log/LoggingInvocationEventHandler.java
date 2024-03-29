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

import static com.palantir.logsafe.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;

/** {@link InvocationEventHandler} that times every method invocation and logs to specified logger. */
public class LoggingInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final ImmutableList<String> MESSAGE_PATTERNS = generateMessagePatterns(20);

    public static final com.palantir.tritium.api.functions.LongPredicate LOG_ALL_DURATIONS = _input -> true;

    public static final com.palantir.tritium.api.functions.LongPredicate LOG_DURATIONS_GREATER_THAN_1_MICROSECOND =
            nanos -> nanos > 1000;

    public static final com.palantir.tritium.api.functions.LongPredicate LOG_DURATIONS_GREATER_THAN_0_MILLIS =
            nanos -> nanos >= 1_000_000;

    public static final com.palantir.tritium.api.functions.LongPredicate NEVER_LOG = _input -> false;

    private final BiConsumer<String, Object[]> logger;
    private final LoggingLevel level;
    private final java.util.function.LongPredicate durationPredicate;

    public LoggingInvocationEventHandler(Logger logger, LoggingLevel level) {
        this(logger, level, (java.util.function.LongPredicate) LOG_ALL_DURATIONS);
    }

    /**
     * Bridge for backward compatibility.
     *
     * @deprecated uSe {@link #LoggingInvocationEventHandler(Logger, LoggingLevel, java.util.function.LongPredicate)}
     */
    @Deprecated
    @SuppressWarnings({"FunctionalInterfaceClash", "InlineMeSuggester"}) // back compat
    public LoggingInvocationEventHandler(
            Logger logger, LoggingLevel level, com.palantir.tritium.api.functions.LongPredicate durationPredicate) {
        this(logger, level, (java.util.function.LongPredicate) durationPredicate);
    }

    @SuppressWarnings("FunctionalInterfaceClash") // back compat
    public LoggingInvocationEventHandler(
            Logger logger, LoggingLevel level, java.util.function.LongPredicate durationPredicate) {
        super((java.util.function.BooleanSupplier)
                createEnabledSupplier(checkNotNull(logger, "logger"), checkNotNull(level, "level")));
        this.level = level;
        this.logger = bindToLevel(logger, level);
        this.durationPredicate = checkNotNull(durationPredicate, "durationPredicate");
    }

    @Override
    public final InvocationContext preInvocation(
            @Nonnull Object instance, @Nonnull Method method, @Nonnull Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public final void onSuccess(@Nullable InvocationContext context, @Nullable Object _result) {
        logInvocation(context);
    }

    @Override
    public final void onFailure(@Nullable InvocationContext context, @Nonnull Throwable _cause) {
        logInvocation(context);
    }

    private void logInvocation(@Nullable InvocationContext context) {
        debugIfNullContext(context);
        if (context != null) {
            long durationNanos = System.nanoTime() - context.getStartTimeNanos();
            logInvocation(context.getMethod(), context.getArgs(), durationNanos);
        }
    }

    private void logInvocation(Method method, @Nullable Object[] nullableArgs, long durationNanos) {
        if (isEnabled() && durationPredicate.test(durationNanos)) {
            Object[] args = nullToEmpty(nullableArgs);
            logger.accept(getMessagePattern(args), getLogParams(method, args, durationNanos, level));
        }
    }

    /**
     * Precompute the logging function based on the configured level.
     * This visitor should be used initially when the handler is created
     * to avoid additional checks on each invocation.
     * <ul>
     *     <li>{@link Logger#trace(String, Object...)}</li>
     *     <li>{@link Logger#debug(String, Object...)}</li>
     *     <li>{@link Logger#info(String, Object...)}</li>
     *     <li>{@link Logger#warn(String, Object...)}</li>
     *     <li>{@link Logger#error(String, Object...)}</li>
     * </ul>
     */
    @SuppressWarnings("NoFunctionalReturnType") // internal functionality
    private static BiConsumer<String, Object[]> bindToLevel(Logger logger, LoggingLevel level) {
        switch (level) {
            case TRACE:
                return logger::trace;
            case DEBUG:
                return logger::debug;
            case INFO:
                return logger::info;
            case WARN:
                return logger::warn;
            case ERROR:
                return logger::error;
        }
        throw invalidLoggingLevel(level);
    }

    private static SafeIllegalArgumentException invalidLoggingLevel(LoggingLevel level) {
        checkNotNull(level, "level");
        return new SafeIllegalArgumentException("Unsupported logging level", SafeArg.of("level", level));
    }

    private static BooleanSupplier createEnabledSupplier(Logger logger, LoggingLevel level) {
        checkNotNull(logger, "logger");
        checkNotNull(level, "level");
        if (getSystemPropertySupplier(LoggingInvocationEventHandler.class).getAsBoolean()) {
            switch (level) {
                case TRACE:
                    return logger::isTraceEnabled;
                case DEBUG:
                    return logger::isDebugEnabled;
                case INFO:
                    return logger::isInfoEnabled;
                case WARN:
                    return logger::isWarnEnabled;
                case ERROR:
                    return logger::isErrorEnabled;
            }
            throw invalidLoggingLevel(level);
        } else {
            return () -> false;
        }
    }

    private static ImmutableList<String> generateMessagePatterns(int maxArgCount) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (int i = 0; i <= maxArgCount; i++) {
            builder.add(generateMessagePattern(i));
        }
        return builder.build();
    }

    static String generateMessagePattern(int argCount) {
        int estimatedSize = 17 + Math.max(0, (argCount * 4) - 2);
        StringBuilder message = new StringBuilder(estimatedSize);
        message.append("{}.{}(");
        for (int i = 0; i < argCount; i++) {
            if (i > 0) {
                message.append(", ");
            }
            message.append("{}");
        }
        message.append(") took {}ms");
        return message.toString();
    }

    static String getMessagePattern(Object[] args) {
        if (args.length < MESSAGE_PATTERNS.size()) {
            return MESSAGE_PATTERNS.get(args.length);
        }
        return generateMessagePattern(args.length);
    }

    static Object[] getLogParams(Method method, Object[] args, long durationNanos, LoggingLevel level) {
        @SuppressWarnings("rawtypes") // arrays don't support generics
        Arg[] logParams = new Arg[3 + args.length];
        logParams[0] = SafeArg.of("class", method.getDeclaringClass().getSimpleName());
        logParams[1] = SafeArg.of("method", method.getName());
        logParams[logParams.length - 1] = SafeArg.of("milliseconds", String.format("%.3f", durationNanos / 1000000.0d));

        Class<?>[] argTypes = method.getParameterTypes();
        if (argTypes.length == 0) {
            return logParams;
        }

        for (int i = 0; i < argTypes.length; i++) {
            String argMessage = argTypes[i].getSimpleName();
            if (level == LoggingLevel.TRACE && i < args.length) {
                Object arg = args[i];
                if (arg instanceof Collection) {
                    argMessage = argMessage + "[" + ((Collection<?>) arg).size() + "]";
                }
            }

            //noinspection ObjectAllocationInLoop - storing allocated arg in array
            logParams[2 + i] = SafeArg.of("type" + i, argMessage);
        }

        return logParams;
    }
}
