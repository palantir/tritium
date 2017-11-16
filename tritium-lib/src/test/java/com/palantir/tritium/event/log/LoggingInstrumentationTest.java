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

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.palantir.tritium.proxy.Instrumentation;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.util.Collections;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

public class LoggingInstrumentationTest {

    private static final String LOG_KEY = SimpleLogger.LOG_KEY_PREFIX + "com.palantir";

    @Nullable
    private String previousLogLevel = null;

    @Before
    public void before() {
        previousLogLevel = System.setProperty(LOG_KEY, LoggingLevel.TRACE.name());
    }

    @After
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
                .withLogging(logger, LoggingLevel.ERROR, LoggingInvocationEventHandler.NEVER_LOG)
                .build();
        assertThat(delegate.invocationCount(), equalTo(0));
        try {
            instrumentedService.test();
        } catch (RuntimeException expected) {
            assertThat(expected.getMessage(), equalTo("expected"));
        }
        assertThat(delegate.invocationCount(), equalTo(1));
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


    private static Logger getLogger() {
        return LoggerFactory.getLogger(LoggingInstrumentationTest.class);
    }

    private static void enableLoggingForLevel(LoggingLevel level) {
        System.setProperty(SimpleLogger.LOG_KEY_PREFIX
                + LoggingInstrumentationTest.class.getName(), level.name());
    }

}
