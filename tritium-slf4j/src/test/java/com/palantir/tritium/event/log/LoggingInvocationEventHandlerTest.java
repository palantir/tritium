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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.impl.SimpleLogger;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

@ExtendWith(SystemStubsExtension.class)
public class LoggingInvocationEventHandlerTest {

    private static final String LOG_KEY = SimpleLogger.LOG_KEY_PREFIX + "com.palantir";

    @SystemStub
    private SystemProperties systemProperties;

    @BeforeEach
    public void before() {
        systemProperties.set(LOG_KEY, LoggingLevel.TRACE.name());
    }

    @AfterEach
    public void after() {
        systemProperties.remove(LOG_KEY);
    }

    @Test
    public void testNullContextOnSuccess() {
        LoggingInvocationEventHandler handler = new LoggingInvocationEventHandler(getLogger(), LoggingLevel.INFO);
        handler.onSuccess(null, "result");
    }

    @Test
    public void testNullContextOnFailure() {
        LoggingInvocationEventHandler handler = new LoggingInvocationEventHandler(getLogger(), LoggingLevel.INFO);
        handler.onFailure(null, new RuntimeException("cause"));
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testLoggingOnSuccess() throws Throwable {
        LoggingInvocationEventHandler handler = new LoggingInvocationEventHandler(getLogger(), LoggingLevel.INFO);

        InvocationContext context = DefaultInvocationContext.of(this, Object.class.getDeclaredMethod("toString"), null);
        handler.onSuccess(context, "result");
    }

    @Test
    // CHECKSTYLE IGNORE IllegalThrows
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testLoggingOnFailure() throws Throwable {
        LoggingInvocationEventHandler handler = new LoggingInvocationEventHandler(getLogger(), LoggingLevel.INFO);

        InvocationContext context = DefaultInvocationContext.of(this, Object.class.getDeclaredMethod("toString"), null);
        handler.onFailure(context, new RuntimeException("cause"));
    }

    private static Logger getLogger() {
        return LoggerFactory.getLogger(LoggingInvocationEventHandlerTest.class);
    }

    @Test
    @SuppressWarnings("NullAway") // explicitly testing null handling
    public void testNullLevel() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new LoggingInvocationEventHandler(getLogger(), null));
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
        Method method =
                TestInterface.class.getDeclaredMethod("multiArgumentMethod", String.class, int.class, Collection.class);
        long durationNanoseconds = 1234567L;
        LoggingLevel level = LoggingLevel.TRACE;

        Object[] args = new Object[method.getParameterTypes().length];
        String messagePattern = LoggingInvocationEventHandler.getMessagePattern(args);
        args[0] = "arg0";
        args[1] = 1;
        args[2] = ImmutableList.of("a", "b", "c");

        Object[] logParams = LoggingInvocationEventHandler.getLogParams(method, args, durationNanoseconds, level);
        String logMessage =
                MessageFormatter.arrayFormat(messagePattern, logParams).getMessage();
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
        String logMessage =
                MessageFormatter.arrayFormat(messagePattern, logParams).getMessage();
        assertThat(logMessage).startsWith("TestInterface.bulk(Set[2]) took 1.235ms");
    }

    @Test
    public void testGetMessagePattern() {
        for (int i = 0; i < 20; i++) {
            assertThat(LoggingInvocationEventHandler.getMessagePattern(new Object[i]))
                    .contains("{}");
        }
    }

    @Test
    public void testBackwardCompatibility() {
        assertThat(LoggingInvocationEventHandler.LOG_DURATIONS_GREATER_THAN_1_MICROSECOND)
                .isInstanceOf(com.palantir.tritium.api.functions.LongPredicate.class);

        java.util.function.LongPredicate legacyPredicate = _ignored -> false;
        assertThat(new LoggingInvocationEventHandler(getLogger(), LoggingLevel.TRACE, legacyPredicate))
                .isNotNull();
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0, 1, 100, 1_000})
    void testLogDurationsLessThanOrEqualToOneMicrosecond(long nanoseconds) {
        assertThat(LoggingInvocationEventHandler.LOG_DURATIONS_GREATER_THAN_1_MICROSECOND.test(nanoseconds))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(longs = {1_001, 1_000_000, Long.MAX_VALUE})
    void testLogDurationsGreaterThanOneMicrosecond(long nanoseconds) {
        assertThat(LoggingInvocationEventHandler.LOG_DURATIONS_GREATER_THAN_1_MICROSECOND.test(nanoseconds))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0, 1, 100, 1_000, 999_999})
    void testLogDurationsLessThanOrEqualToZeroMilliseconds(long nanoseconds) {
        assertThat(LoggingInvocationEventHandler.LOG_DURATIONS_GREATER_THAN_0_MILLIS.test(nanoseconds))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(longs = {1_000_000, 1_000_001, 1_000_000_000, Long.MAX_VALUE})
    void testLogDurationsGreaterThanZeroMilliseconds(long nanoseconds) {
        assertThat(LoggingInvocationEventHandler.LOG_DURATIONS_GREATER_THAN_0_MILLIS.test(nanoseconds))
                .isTrue();
    }
}
