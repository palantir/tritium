/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event.log;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.palantir.tritium.event.AbstractInvocationEventHandler;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongPredicate;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link com.palantir.tritium.event.InvocationEventHandler} that times every method invocation and logs to specified
 * logger.
 */
public class LoggingInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingInvocationEventHandler.class);

    public static final LongPredicate LOG_ALL_DURATIONS = nanos -> true;
    public static final LongPredicate LOG_DURATIONS_GREATER_THAN_1_MICROSECOND = nanos -> {
        return TimeUnit.MICROSECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 1;
    };
    public static final LongPredicate LOG_DURATIONS_GREATER_THAN_0_MILLIS = nanos -> {
        return TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS) > 0;
    };

    private static final List<String> MESSAGE_PATTERNS = generateMessagePatterns(10);

    private final Logger logger;
    private final LoggingLevel level;
    private final LongPredicate durationPredicate;

    public LoggingInvocationEventHandler(Logger logger, LoggingLevel level) {
        this(logger, level, LOG_ALL_DURATIONS);
    }

    public LoggingInvocationEventHandler(Logger logger, LoggingLevel level, LongPredicate durationPredicate) {
        super(createEnabledSupplier(logger, level));
        this.logger = checkNotNull(logger, "logger");
        this.level = checkNotNull(level, "level");
        this.durationPredicate = checkNotNull(durationPredicate, "durationPredicate");
    }

    @Override
    public final InvocationContext preInvocation(Object instance, Method method, Object[] args) {
        return DefaultInvocationContext.of(instance, method, args);
    }

    @Override
    public final void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
        if (context == null) {
            LOGGER.debug("Encountered null metric context likely due to exception in preInvocation");
            return;
        }

        long durationNanos = System.nanoTime() - context.getStartTimeNanos();
        logInvocation(context.getMethod(), context.getArgs(), durationNanos);
    }

    @Override
    public final void onFailure(@Nullable InvocationContext context, Throwable cause) {
        if (context == null) {
            LOGGER.debug("Encountered null metric context likely due to exception in preInvocation");
            return;
        }

        LOGGER.warn("Failed {}", cause, cause);
    }

    private void logInvocation(Method method, @Nullable Object[] nullableArgs, long durationNanos) {
        if (isEnabled() && durationPredicate.test(durationNanos)) {
            Object[] args = nullToEmpty(nullableArgs);
            String messagePattern = getMessagePattern(args);
            Object[] logParams = getLogParams(method, args, durationNanos, level);
            log(messagePattern, logParams);
        }
    }

    private void log(String message, Object... args) {
        if (level == LoggingLevel.TRACE) {
            logger.trace(message, args);
        } else if (level == LoggingLevel.DEBUG) {
            logger.debug(message, args);
        } else if (level == LoggingLevel.INFO) {
            logger.info(message, args);
        } else if (level == LoggingLevel.WARN) {
            logger.warn(message, args);
        } else if (level == LoggingLevel.ERROR) {
            logger.error(message, args);
        } else {
            throw new IllegalArgumentException("Unsupported logging level " + level);
        }
    }

    static boolean isEnabled(Logger logger, LoggingLevel level) {
        checkNotNull(level);
        if (level == LoggingLevel.TRACE) {
            return logger.isTraceEnabled();
        } else if (level == LoggingLevel.DEBUG) {
            return logger.isDebugEnabled();
        } else if (level == LoggingLevel.INFO) {
            return logger.isInfoEnabled();
        } else if (level == LoggingLevel.WARN) {
            return logger.isWarnEnabled();
        } else if (level == LoggingLevel.ERROR) {
            return logger.isErrorEnabled();
        } else {
            throw new IllegalArgumentException("Unsupported logging level " + level);
        }
    }

    private static BooleanSupplier createEnabledSupplier(Logger logger, LoggingLevel level) {
        checkNotNull(logger, "logger");
        checkNotNull(level, "level");
        if (getSystemPropertySupplier(LoggingInvocationEventHandler.class).getAsBoolean()) {
            return () -> isEnabled(logger, level);
        } else {
            return () -> false;
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
        Verify.verify(estimatedSize == message.length(),
                "Incorrect estimated size for %s arguments, expected %s, was %s",
                argCount, estimatedSize, message.length());
        return message.toString();
    }

    static String getMessagePattern(Object[] args) {
        if (args.length < MESSAGE_PATTERNS.size()) {
            return MESSAGE_PATTERNS.get(args.length);
        }
        return generateMessagePattern(args.length);
    }

    static Object[] getLogParams(Method method, Object[] args, long durationNanos, LoggingLevel level) {
        Object[] logParams = new Object[3 + args.length];
        logParams[0] = method.getDeclaringClass().getSimpleName();
        logParams[1] = method.getName();
        logParams[logParams.length - 1] = String.format("%.3f", durationNanos / 1_000_000.0d);

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

            logParams[2 + i] = argMessage;
        }

        return logParams;
    }

}
