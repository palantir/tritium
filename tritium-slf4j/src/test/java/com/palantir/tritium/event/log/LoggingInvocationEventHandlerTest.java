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

import com.palantir.tritium.v1.api.event.InvocationContext;
import com.palantir.tritium.v1.core.event.DefaultInvocationContext;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

@SuppressWarnings("deprecation") // explicitly testing deprecated functionality
public class LoggingInvocationEventHandlerTest {

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
    @SuppressWarnings("deprecation") // testing backward compatibility
    public void testBackwardCompatibility() {
        assertThat(LoggingInvocationEventHandler.LOG_DURATIONS_GREATER_THAN_1_MICROSECOND)
                .isInstanceOf(com.palantir.tritium.api.functions.LongPredicate.class);

        java.util.function.LongPredicate legacyPredicate = _ignored -> false;
        assertThat(new LoggingInvocationEventHandler(getLogger(), LoggingLevel.TRACE, legacyPredicate))
                .isNotNull();
    }
}
