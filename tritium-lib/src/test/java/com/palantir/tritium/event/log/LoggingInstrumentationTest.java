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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.palantir.tritium.proxy.Instrumentation;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.util.Collections;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

public class LoggingInstrumentationTest {

    private static final String LOG_KEY = SimpleLogger.LOG_KEY_PREFIX + "com.palantir";

    @Nullable
    private String previousLogLevel = null;

    @BeforeEach
    public void before() {
        previousLogLevel = System.setProperty(LOG_KEY, LoggingLevel.TRACE.name());
    }

    @AfterEach
    public void after() {
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

    @Test
    public void testNoLogging() {
        TestImplementation delegate = new TestImplementation();
        Logger logger = getLogger();
        @SuppressWarnings("deprecation") // explicitly testing
        Runnable instrumentedService = Instrumentation.builder(Runnable.class, delegate)
                .withLogging(logger, LoggingLevel.TRACE, LoggingInvocationEventHandler.LOG_ALL_DURATIONS)
                .build();
        assertThat(delegate.invocationCount()).isZero();
        instrumentedService.run();
        assertThat(delegate.invocationCount()).isOne();
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
        @SuppressWarnings("deprecation") // explicitly testing
        TestInterface instrumentedService = Instrumentation.builder(TestInterface.class, delegate)
                .withLogging(logger, LoggingLevel.ERROR, LoggingInvocationEventHandler.NEVER_LOG)
                .build();
        assertThat(delegate.invocationCount()).isZero();
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(instrumentedService::test)
                .withMessage("expected");
        assertThat(delegate.invocationCount()).isOne();
    }

    private static void testLoggingAtLevel(LoggingLevel level) {
        TestImplementation delegate = new TestImplementation();
        assertThat(level)
                .isIn(LoggingLevel.ERROR, LoggingLevel.WARN, LoggingLevel.INFO, LoggingLevel.DEBUG, LoggingLevel.TRACE);
        enableLoggingForLevel(level);
        Logger logger = getLogger();

        LoggingInvocationEventHandler handler = new LoggingInvocationEventHandler(logger, level);
        TestInterface instrumented = Instrumentation.builder(TestInterface.class, delegate)
                .withHandler(handler)
                .build();

        assertThat(handler.isEnabled()).isTrue();

        assertThat(delegate.invocationCount()).isZero();

        instrumented.multiArgumentMethod("test", 1, Collections.singletonList("hello"));

        switch (level) {
            case ERROR:
                assertThat(logger.isErrorEnabled()).isTrue();
                break;
            case WARN:
                assertThat(logger.isWarnEnabled()).isTrue();
                break;
            case INFO:
                assertThat(logger.isInfoEnabled()).isTrue();
                break;
            case DEBUG:
                assertThat(logger.isDebugEnabled()).isTrue();
                break;
            case TRACE:
                assertThat(logger.isTraceEnabled()).isTrue();
                break;
        }
        assertThat(delegate.invocationCount()).isOne();
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(LoggingInstrumentationTest.class);
    }

    private static void enableLoggingForLevel(LoggingLevel level) {
        System.setProperty(SimpleLogger.LOG_KEY_PREFIX + LoggingInstrumentationTest.class.getName(), level.name());
    }
}
