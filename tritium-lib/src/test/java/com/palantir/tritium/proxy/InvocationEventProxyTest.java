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

package com.palantir.tritium.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.palantir.tritium.api.event.InstrumentationFilter;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationFilters;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("NullAway") // mock injection
public class InvocationEventProxyTest {

    private static final Object[] EMPTY_ARGS = {};

    @Mock
    private InstrumentationFilter mockFilter;
    @Mock
    private InvocationEventHandler<InvocationContext> mockHandler;

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testDisabled() throws Throwable {
        InvocationEventProxy proxy = new InvocationEventProxy(
                Collections.emptyList(), InstrumentationFilters.from((BooleanSupplier) () -> false)) {
            @Override
            Object getDelegate() {
                return "disabled";
            }
        };
        proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS);
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentPreInvocation() throws Throwable {
        InvocationEventHandler<InvocationContext> testHandler = new SimpleHandler();
        InvocationEventProxy proxy = createTestProxy(testHandler);

        assertThat(proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS))
                .isNotNull().extracting(Object::toString).isEqualTo("test");

        Object result2 = proxy.handlePreInvocation(this, getToStringMethod(), EMPTY_ARGS);
        assertThat(result2).isInstanceOf(DefaultInvocationContext.class);
        assertThat(Objects.requireNonNull(result2).toString()).contains(InvocationEventProxyTest.class.getName());

        InvocationContext context = proxy.handlePreInvocation(this, getToStringMethod(), EMPTY_ARGS);
        assertThat(Objects.requireNonNull(context).toString())
                .contains("startTimeNanos")
                .contains("instance")
                .contains("method");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentPreInvocationThrows() throws Throwable {
        InvocationEventHandler<InvocationContext> testHandler = new SimpleHandler() {
            @Override
            public InvocationContext preInvocation(
                    @Nonnull Object instance,
                    @Nonnull Method method,
                    @Nonnull Object[] args) {
                throw new IllegalStateException("expected");
            }
        };
        InvocationEventProxy proxy = createTestProxy(testHandler);

        Object result = proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS);

        assertThat(Objects.requireNonNull(result).toString()).isEqualTo("test");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentOnSuccessThrows() throws Throwable {
        InvocationEventHandler<InvocationContext> testHandler = new SimpleHandler() {
            @Override
            public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
                throw new IllegalStateException("expected");
            }
        };

        InvocationEventProxy proxy = createTestProxy(testHandler);

        Object result = proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS);

        assertThat(Objects.requireNonNull(result).toString()).isEqualTo("test");

        InvocationContext context = DefaultInvocationContext.of(this, getToStringMethod(), null);
        proxy.handleOnSuccess(context, result);
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentOnFailureThrows() throws Throwable {
        InvocationEventHandler<InvocationContext> testHandler = new SimpleHandler() {
            @Override
            public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {
                throw new IllegalStateException("expected");
            }

            @Override
            public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {
                throw new IllegalStateException("expected");
            }
        };

        InvocationEventProxy proxy = createTestProxy(testHandler);

        Object result = proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS);
        assertThat(Objects.requireNonNull(result).toString()).isEqualTo("test");

        InvocationContext context = DefaultInvocationContext.of(this, getToStringMethod(), null);
        RuntimeException expected = new RuntimeException("expected");
        Throwable throwable = proxy.handleOnFailure(context, expected);
        assertThat(throwable).isSameAs(expected);
    }

    @Test
    public void testToInvocationDebugString() throws Exception {
        Throwable cause = new RuntimeException("cause");
        InvocationEventProxy.logInvocationWarning("test", this, getToStringMethod(), cause);
    }

    @Test
    public void testToInvocationContextDebugString() throws Exception {
        Throwable cause = new RuntimeException("cause");
        InvocationContext context = DefaultInvocationContext.of("test", getToStringMethod(), EMPTY_ARGS);
        Object result = "Hello, World!";
        InvocationEventProxy.logInvocationWarning("test", context, result, cause);
    }

    @Test
    public void testInstrumentToString() {
        List<InvocationEventHandler<InvocationContext>> handlers = Collections.emptyList();
        InvocationEventProxy proxy = new InvocationEventProxy(handlers) {
            @Override
            Object getDelegate() {
                return "Hello, world";
            }
        };

        assertThat(proxy.toString()).isEqualTo("Hello, world");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentInvocation() throws Throwable {
        InvocationEventProxy proxy = createTestProxy(new SimpleHandler());

        assertThat(proxy.instrumentInvocation("test", getToStringMethod(), null)).isEqualTo("test");
        assertThat(proxy.instrumentInvocation("test", getToStringMethod(), EMPTY_ARGS)).isEqualTo("test");
        assertThatThrownBy(() -> proxy.instrumentInvocation("test", getToStringMethod(), new Object[] {"Hello"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("wrong number of arguments");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentInvocationThrowsException() {
        InvocationEventProxy proxy = createSimpleTestProxy();

        assertThatThrownBy(() -> proxy.instrumentInvocation("test", getThrowsCheckedExceptionMethod(), null))
                .isInstanceOf(TestImplementation.TestException.class)
                .hasMessage("Testing checked Exception handling");

        assertThatThrownBy(() -> proxy.instrumentInvocation("test", getThrowsThrowableMethod(), null))
                .isInstanceOf(TestImplementation.TestThrowable.class)
                .hasMessage("TestThrowable");

        assertThatThrownBy(() -> proxy.instrumentInvocation("test", getThrowsOutOfMemoryErrorMethod(), null))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("Testing OOM");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testExecute() throws Throwable {
        InvocationEventProxy proxy = createTestProxy(new SimpleHandler());

        assertThat(proxy.execute(getToStringMethod(), null)).isEqualTo("test");
        assertThat(proxy.execute(getToStringMethod(), EMPTY_ARGS)).isEqualTo("test");
        assertThatThrownBy(() -> proxy.execute(getToStringMethod(), new Object[] {"Hello"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("wrong number of arguments");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testExecuteThrowsExceptions() {
        InvocationEventProxy proxy = createSimpleTestProxy();

        assertThatThrownBy(() -> proxy.execute(getThrowsCheckedExceptionMethod(), null))
                .isInstanceOf(TestImplementation.TestException.class)
                .hasMessage("Testing checked Exception handling");

        assertThatThrownBy(() -> proxy.execute(getThrowsThrowableMethod(), null))
                .isInstanceOf(TestImplementation.TestThrowable.class)
                .hasMessage("TestThrowable");

        assertThatThrownBy(() -> proxy.execute(getThrowsOutOfMemoryErrorMethod(), null))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("Testing OOM");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testThrowingFilterAndHandler() throws Throwable {
        doThrow(new IllegalStateException("test isEnabled"))
                .when(mockHandler)
                .isEnabled();

        InvocationEventProxy proxy = createTestProxy(mockHandler, mockFilter);
        assertThat(proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS)).isEqualTo("test");

        verify(mockHandler).isEnabled();
        verifyNoMoreInteractions(mockFilter);
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testThrowingFilter() throws Throwable {
        doThrow(new UnsupportedOperationException("test shouldInstrument"))
                .when(mockFilter)
                .shouldInstrument(any(), any(), any());
        when(mockHandler.isEnabled()).thenReturn(true);

        InvocationEventProxy proxy = createTestProxy(mockHandler, mockFilter);
        assertThat(proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS)).isEqualTo("test");

        verify(mockHandler).isEnabled();
        verify(mockFilter).shouldInstrument(any(), any(), any());
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testThrowingHandler() throws Throwable {
        doThrow(new IllegalStateException("test isEnabled"))
                .when(mockHandler)
                .isEnabled();

        InvocationEventProxy proxy = createTestProxy(mockHandler);
        assertThat(proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS)).isEqualTo("test");

        verify(mockHandler).isEnabled();
        verifyZeroInteractions(mockFilter);
    }

    private static InvocationEventProxy createSimpleTestProxy() {
        return new TestProxy(
                new TestImplementation(),
                ImmutableList.of(new SimpleHandler()),
                InstrumentationFilters.INSTRUMENT_ALL);
    }

    private static InvocationEventProxy createTestProxy(
            InvocationEventHandler<InvocationContext> handler,
            InstrumentationFilter filter) {
        return new TestProxy("test", ImmutableList.of(handler), filter);
    }

    private static InvocationEventProxy createTestProxy(InvocationEventHandler<InvocationContext> handler) {
        return createTestProxy(handler, InstrumentationFilters.INSTRUMENT_ALL);
    }

    private static Method getToStringMethod() throws NoSuchMethodException {
        return Object.class.getDeclaredMethod("toString");
    }

    private static Method getThrowsOutOfMemoryErrorMethod() throws NoSuchMethodException {
        return TestInterface.class.getMethod("throwsOutOfMemoryError");
    }

    private static Method getThrowsThrowableMethod() throws NoSuchMethodException {
        return TestInterface.class.getMethod("throwsThrowable");
    }

    private static Method getThrowsCheckedExceptionMethod() throws NoSuchMethodException {
        return TestInterface.class.getMethod("throwsCheckedException");
    }

    private static class SimpleHandler implements InvocationEventHandler<InvocationContext> {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public InvocationContext preInvocation(
                @Nonnull Object instance,
                @Nonnull Method method,
                @Nonnull Object[] args) {
            return DefaultInvocationContext.of(instance, method, args);
        }

        @Override
        public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {}

        @Override
        public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {}
    }

    private static class TestProxy extends InvocationEventProxy {
        private final Object delegate;

        TestProxy(
                Object delegate,
                List<InvocationEventHandler<InvocationContext>> handlers,
                InstrumentationFilter filter) {
            super(handlers, filter);
            this.delegate = delegate;
        }

        @Override
        Object getDelegate() {
            return delegate;
        }
    }
}
