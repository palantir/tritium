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
import com.palantir.tritium.api.functions.BooleanSupplier;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link InvocationEventHandler} that times every method invocation and logs to specified
 * logger.
 */
public class LoggingInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger CLASS_LOGGER = LoggerFactory.getLogger(LoggingInvocationEventHandler.class);
    private static final List<String> MESSAGE_PATTERNS = generateMessagePatterns(20);

    public static final com.palantir.tritium.api.functions.LongPredicate LOG_ALL_DURATIONS =
            com.palantir.tritium.api.functions.LongPredicate.TRUE;

    public static final com.palantir.tritium.api.functions.LongPredicate LOG_DURATIONS_GREATER_THAN_1_MICROSECOND =
            nanos -> TimeUnit.MICROSECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 1;

    public static final com.palantir.tritium.api.functions.LongPredicate LOG_DURATIONS_GREATER_THAN_0_MILLIS =
            nanos -> TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 0;

    public static final com.palantir.tritium.api.functions.LongPredicate NEVER_LOG =
            com.palantir.tritium.api.functions.LongPredicate.FALSE;

    private final Logger logger;
    private final LoggingLevel level;
    private final java.util.function.LongPredicate durationPredicate;

    public LoggingInvocationEventHandler(Logger logger, LoggingLevel level) {
        this(logger, level, (java.util.function.LongPredicate) LOG_ALL_DURATIONS);
    }

    /**
     * Bridge for backward compatibility.
     * @deprecated uSe {@link #LoggingInvocationEventHandler(Logger, LoggingLevel, java.util.function.LongPredicate)}
     */
    @Deprecated
    @SuppressWarnings("FunctionalInterfaceClash") // back compat
    public LoggingInvocationEventHandler(Logger logger, LoggingLevel level,
            com.palantir.tritium.api.functions.LongPredicate durationPredicate) {
        this(logger, level, (java.util.function.LongPredicate) durationPredicate);
    }

    @SuppressWarnings("FunctionalInterfaceClash") // back compat
    public LoggingInvocationEventHandler(Logger logger, LoggingLevel level,
            java.util.function.LongPredicate durationPredicate) {
        super((java.util.function.BooleanSupplier) createEnabledSupplier(logger, level));
        this.logger = checkNotNull(logger, "logger");
        this.level = checkNotNull(level, "level");
        this.durationPredicate = checkNotNull(durationPredicate, "durationPredicate");
    }

    @Override
    public final InvocationContext preInvocation(
            @Nonnull Object instance,
            @Nonnull Method method,
            @Nonnull Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public final void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        if (context == null) {
            CLASS_LOGGER.debug("Encountered null metric context likely due to exception in preInvocation");
            return;
        }

        long durationNanos = System.nanoTime() - context.getStartTimeNanos();
        logInvocation(context.getMethod(), context.getArgs(), durationNanos);
    }

    @Override
    public final void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
        if (context == null) {
            CLASS_LOGGER.debug("Encountered null metric context likely due to exception in preInvocation");
            return;
        }

        long durationNanos = System.nanoTime() - context.getStartTimeNanos();
        logInvocation(context.getMethod(), context.getArgs(), durationNanos);
    }

    private void logInvocation(Method method, @Nullable Object[] nullableArgs, long durationNanos) {
        if (isEnabled() && durationPredicate.test(durationNanos)) {
            Object[] args = nullToEmpty(nullableArgs);
            log(getMessagePattern(args), getLogParams(method, args, durationNanos, level));
        }
    }

    // All message formats are generated with placeholders and safe args
    @SuppressWarnings({"Slf4jConstantLogMessage", "Slf4jLogsafeArgs", "Var"})
    private void log(final String messageFormat, Object... args) {
        switch (level) {
            case TRACE:
                logger.trace(messageFormat, args);
                return;
            case DEBUG:
                logger.debug(messageFormat, args);
                return;
            case INFO:
                logger.info(messageFormat, args);
                return;
            case WARN:
                logger.warn(messageFormat, args);
                return;
            case ERROR:
                logger.error(messageFormat, args);
                return;
        }
        throw invalidLoggingLevel(level);
    }

    static boolean isEnabled(Logger logger, LoggingLevel level) {
        switch (level) {
            case TRACE:
                return logger.isTraceEnabled();
            case DEBUG:
                return logger.isDebugEnabled();
            case INFO:
                return logger.isInfoEnabled();
            case WARN:
                return logger.isWarnEnabled();
            case ERROR:
                return logger.isErrorEnabled();
        }
        throw invalidLoggingLevel(level);
    }

    private static IllegalArgumentException invalidLoggingLevel(LoggingLevel level) {
        return new IllegalArgumentException("Unsupported logging level " + level);
    }

    private static BooleanSupplier createEnabledSupplier(Logger logger, LoggingLevel level) {
        checkNotNull(logger, "logger");
        checkNotNull(level, "level");
        if (getSystemPropertySupplier(LoggingInvocationEventHandler.class).getAsBoolean()) {
            return () -> isEnabled(logger, level);
        } else {
            return BooleanSupplier.FALSE;
        }
    }

    private static List<String> generateMessagePatterns(int maxArgCount) {
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

            logParams[2 + i] = SafeArg.of("type" + i, argMessage);
        }

        return logParams;
    }

}
