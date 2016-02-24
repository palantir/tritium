/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event.log;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.proxy.Instrumentation;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.impl.SimpleLogger;

public final class LoggingInvocationEventHandlerTest {

    private static final String LOG_KEY = SimpleLogger.LOG_KEY_PREFIX + "com.palantir";

    @Nullable private String previousLogLevel = null;

    @Before
    public void setUp() {
        previousLogLevel = System.setProperty(LOG_KEY, LoggingLevel.TRACE.name());
    }

    @After
    public void tearDown() {
        if (previousLogLevel == null) {
            System.clearProperty(LOG_KEY);
        } else {
            System.setProperty(LOG_KEY, previousLogLevel);
        }
    }

    @Test
    public void testLogging() {
        for (LoggingLevel loggingLevel : LoggingLevel.values()) {
            testLoggingAtLevel(loggingLevel);
        }
    }

    private void testLoggingAtLevel(LoggingLevel level) {
        TestImplementation delegate = new TestImplementation();
        enableLoggingForLevel(level);
        Logger logger = getLogger();

        LoggingInvocationEventHandler handler = new LoggingInvocationEventHandler(logger, level);
        TestInterface instrumented = Instrumentation.builder(TestInterface.class, delegate)
                .withHandler(handler)
                .build();

        assertThat(LoggingInvocationEventHandler.isEnabled(logger, level), equalTo(true));

        assertThat(delegate.invocationCount(), equalTo(0));

        instrumented.multiArgumentMethod("test", 1, Collections.singletonList("hello"));

        switch (level) {
            case ERROR:
                assertThat(logger.isErrorEnabled(), equalTo(true));
                break;
            case WARN:
                assertThat(logger.isWarnEnabled(), equalTo(true));
                break;
            case INFO:
                assertThat(logger.isInfoEnabled(), equalTo(true));
                break;
            case DEBUG:
                assertThat(logger.isDebugEnabled(), equalTo(true));
                break;
            case TRACE:
                assertThat(logger.isTraceEnabled(), equalTo(true));
                break;

            default:
                fail("Invalid level: " + level);
                break;
        }
        assertThat(delegate.invocationCount(), equalTo(1));
    }

    @Test
    public void testNoLogging() {
        TestImplementation delegate = new TestImplementation();
        Logger logger = getLogger();
        Runnable instrumentedService = Instrumentation.builder(Runnable.class, delegate)
                .withLogging(logger, LoggingLevel.TRACE, LoggingInvocationEventHandler.LOG_ALL_DURATIONS)
                .build();
        assertThat(delegate.invocationCount(), equalTo(0));
        instrumentedService.run();
        assertThat(delegate.invocationCount(), equalTo(1));
    }

    @Test
    public void testErrorLogging() {
        TestImplementation delegate = new TestImplementation() {
            @Override
            public String test() {
                super.test();
                throw new RuntimeException("expected");
            }
        };
        Logger logger = getLogger();
        TestInterface instrumentedService = Instrumentation.builder(TestInterface.class, delegate)
                .withLogging(logger, LoggingLevel.ERROR, nanos -> false)
                .build();
        assertThat(delegate.invocationCount(), equalTo(0));
        try {
            instrumentedService.test();
        } catch (RuntimeException expected) {
            assertThat(expected.getMessage(), equalTo("expected"));
        }
        assertThat(delegate.invocationCount(), equalTo(1));
    }

    @Test
    public void testNullContextOnSuccess() {
        LoggingInvocationEventHandler handler = new LoggingInvocationEventHandler(
                getLogger(), LoggingLevel.INFO);
        handler.onSuccess(null, "result");
    }

    @Test
    public void testNullContextOnFailure() {
        LoggingInvocationEventHandler handler = new LoggingInvocationEventHandler(
                getLogger(), LoggingLevel.INFO);
        handler.onFailure(null, new RuntimeException("cause"));
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testLoggingOnSuccess() throws Throwable {
        LoggingInvocationEventHandler handler = new LoggingInvocationEventHandler(
                getLogger(), LoggingLevel.INFO);

        InvocationContext context = DefaultInvocationContext.of(this, Object.class.getDeclaredMethod("toString"), null);
        handler.onSuccess(context, "result");
    }

    @Test
    //CHECKSTYLE IGNORE IllegalThrows
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testLoggingOnFailure() throws Throwable {
        LoggingInvocationEventHandler handler = new LoggingInvocationEventHandler(
                getLogger(), LoggingLevel.INFO);

        InvocationContext context = DefaultInvocationContext.of(this, Object.class.getDeclaredMethod("toString"), null);
        handler.onFailure(context, new RuntimeException("cause"));

    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(LoggingInvocationEventHandlerTest.class);
    }

    @Test(expected = NullPointerException.class)
    @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
    public void testNullIsEnabled() {
        //noinspection ConstantConditions
        LoggingInvocationEventHandler.isEnabled(getLogger(), null);
    }

    @Test(expected = NullPointerException.class)
    @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
    public void testNullLevel() {
        //noinspection ConstantConditions
        new LoggingInvocationEventHandler(getLogger(), null);
    }

    @Test
    public void testGenerateMessagePattern() {
        assertThat(LoggingInvocationEventHandler.generateMessagePattern(0), equalTo("{}.{}() took {}ms"));
        assertThat(LoggingInvocationEventHandler.generateMessagePattern(1), equalTo("{}.{}({}) took {}ms"));
        assertThat(LoggingInvocationEventHandler.generateMessagePattern(2), equalTo("{}.{}({}, {}) took {}ms"));
        assertThat(LoggingInvocationEventHandler.generateMessagePattern(3), equalTo("{}.{}({}, {}, {}) took {}ms"));
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testGenerateMessage() throws Throwable {
        Method method = TestInterface.class.getDeclaredMethod("multiArgumentMethod",
                String.class, int.class, Collection.class);
        long durationNanos = 1234567L;
        LoggingLevel level = LoggingLevel.TRACE;

        Object[] args = new Object[method.getParameterCount()];
        String messagePattern = LoggingInvocationEventHandler.getMessagePattern(args);
        args[0] = "arg0";
        args[1] = 1;
        args[2] = ImmutableList.of("a", "b", "c");

        Object[] logParams = LoggingInvocationEventHandler.getLogParams(method, args, durationNanos, level);
        String logMessage = MessageFormatter.arrayFormat(messagePattern, logParams).getMessage();
        assertThat(logMessage,
                startsWith("TestInterface.multiArgumentMethod(String, int, Collection[3]) took 1.235ms"));
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testGenerateCollectionsMessage() throws Throwable {
        Method method = TestInterface.class.getDeclaredMethod("bulk", Set.class);
        long durationNanos = 1234567L;
        LoggingLevel level = LoggingLevel.TRACE;

        Object[] args = new Object[method.getParameterCount()];
        String messagePattern = LoggingInvocationEventHandler.getMessagePattern(args);
        args[0] = ImmutableSet.of("a", "b");
        Object[] logParams = LoggingInvocationEventHandler.getLogParams(method, args, durationNanos, level);
        String logMessage = MessageFormatter.arrayFormat(messagePattern, logParams).getMessage();
        assertThat(logMessage,
                startsWith("TestInterface.bulk(Set[2]) took 1.235ms"));
    }

    @Test
    public void testGetMessagePattern() {
        for (int i = 0; i < 20; i++) {
            assertThat(LoggingInvocationEventHandler.getMessagePattern(new Object[i]),
                    containsString("{}"));
        }
    }

    private static void enableLoggingForLevel(LoggingLevel level) {
        System.setProperty(SimpleLogger.LOG_KEY_PREFIX
                + LoggingInvocationEventHandlerTest.class.getName(), level.name());
    }

}
