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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.impl.SimpleLogger;

public class LoggingInvocationEventHandlerTest {

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
    public void testNullIsEnabled() {
        //noinspection ConstantConditions
        LoggingInvocationEventHandler.isEnabled(getLogger(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testNullLevel() {
        //noinspection ConstantConditions
        new LoggingInvocationEventHandler(getLogger(), null);
    }

    @Test
    public void testGenerateMessagePattern() {
        assertThat(LoggingInvocationEventHandler.generateMessagePattern(0)).isEqualTo("{}.{}() took {}ms");
        assertThat(LoggingInvocationEventHandler.generateMessagePattern(1)).isEqualTo("{}.{}({}) took {}ms");
        assertThat(LoggingInvocationEventHandler.generateMessagePattern(2)).isEqualTo("{}.{}({}, {}) took {}ms");
        assertThat(LoggingInvocationEventHandler.generateMessagePattern(3)).isEqualTo("{}.{}({}, {}, {}) took {}ms");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testGenerateMessage() throws Throwable {
        Method method = TestInterface.class.getDeclaredMethod("multiArgumentMethod",
                String.class, int.class, Collection.class);
        long durationNanoseconds = 1234567L;
        LoggingLevel level = LoggingLevel.TRACE;

        Object[] args = new Object[method.getParameterTypes().length];
        String messagePattern = LoggingInvocationEventHandler.getMessagePattern(args);
        args[0] = "arg0";
        args[1] = 1;
        args[2] = ImmutableList.of("a", "b", "c");

        Object[] logParams = LoggingInvocationEventHandler.getLogParams(method, args, durationNanoseconds, level);
        String logMessage = MessageFormatter.arrayFormat(messagePattern, logParams).getMessage();
        assertThat(logMessage).startsWith("TestInterface.multiArgumentMethod(String, int, Collection[3]) took 1.235ms");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testGenerateCollectionsMessage() throws Throwable {
        Method method = TestInterface.class.getDeclaredMethod("bulk", Set.class);
        long durationNanoseconds = 1234567L;
        LoggingLevel level = LoggingLevel.TRACE;

        Object[] args = new Object[method.getParameterTypes().length];
        String messagePattern = LoggingInvocationEventHandler.getMessagePattern(args);
        args[0] = ImmutableSet.of("a", "b");
        Object[] logParams = LoggingInvocationEventHandler.getLogParams(method, args, durationNanoseconds, level);
        String logMessage = MessageFormatter.arrayFormat(messagePattern, logParams).getMessage();
        assertThat(logMessage).startsWith("TestInterface.bulk(Set[2]) took 1.235ms");
    }

    @Test
    public void testGetMessagePattern() {
        for (int i = 0; i < 20; i++) {
            assertThat(LoggingInvocationEventHandler.getMessagePattern(new Object[i])).contains("{}");
        }
    }

    @Test
    public void testBackwardCompatibility() {
        assertThat(LoggingInvocationEventHandler.LOG_DURATIONS_GREATER_THAN_1_MICROSECOND)
                .isInstanceOf(com.palantir.tritium.api.functions.LongPredicate.class);

        com.palantir.tritium.api.functions.LongPredicate legacyPredicate = input -> false;
        assertThat(new LoggingInvocationEventHandler(getLogger(), LoggingLevel.TRACE, legacyPredicate)).isNotNull();
    }

}
