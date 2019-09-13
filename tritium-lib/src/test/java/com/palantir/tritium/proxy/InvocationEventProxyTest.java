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
import java.util.function.BooleanSupplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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
        assertThat(proxy.invoke(this, getStringLengthMethod(), EMPTY_ARGS)).isEqualTo("disabled".length());
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentPreInvocation() throws Throwable {
        InvocationEventHandler<InvocationContext> testHandler = new SimpleHandler();
        InvocationEventProxy proxy = createTestProxy(testHandler);

        assertThat(proxy.invoke(this, getStringLengthMethod(), EMPTY_ARGS))
                .isEqualTo("test".length());

        Object result2 = proxy.handlePreInvocation(this, getStringLengthMethod(), EMPTY_ARGS);
        assertThat(result2).isInstanceOf(DefaultInvocationContext.class)
                .asString().contains(InvocationEventProxyTest.class.getName());

        InvocationContext context = proxy.handlePreInvocation(this, getStringLengthMethod(), EMPTY_ARGS);
        assertThat(context).asString()
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
                    @Nonnull Object unusedInstance,
                    @Nonnull Method unusedMethod,
                    @Nonnull Object[] unusedArgs) {
                throw new IllegalStateException("expected");
            }
        };
        InvocationEventProxy proxy = createTestProxy(testHandler);

        Object result = proxy.invoke(this, getStringLengthMethod(), EMPTY_ARGS);

        assertThat(result).isEqualTo("test".length());
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentOnSuccessThrows() throws Throwable {
        InvocationEventHandler<InvocationContext> testHandler = new SimpleHandler() {
            @Override
            public void onSuccess(@Nullable InvocationContext unusedContext, @Nullable Object unusedResult) {
                throw new IllegalStateException("expected");
            }
        };

        InvocationEventProxy proxy = createTestProxy(testHandler);

        Object result = proxy.invoke(this, getStringLengthMethod(), EMPTY_ARGS);

        assertThat(result).isEqualTo("test".length());

        InvocationContext context = DefaultInvocationContext.of(this, getStringLengthMethod(), null);
        proxy.handleOnSuccess(context, result);
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentOnFailureThrows() throws Throwable {
        InvocationEventHandler<InvocationContext> testHandler = new SimpleHandler() {
            @Override
            public void onSuccess(@Nullable InvocationContext unusedContext, @Nullable Object unusedResult) {
                throw new IllegalStateException("expected");
            }

            @Override
            public void onFailure(@Nullable InvocationContext unusedContext, @Nonnull Throwable unusedCause) {
                throw new IllegalStateException("expected");
            }
        };

        InvocationEventProxy proxy = createTestProxy(testHandler);

        Object result = proxy.invoke(this, getStringLengthMethod(), EMPTY_ARGS);
        assertThat(result).isEqualTo("test".length());

        InvocationContext context = DefaultInvocationContext.of(this, getStringLengthMethod(), null);
        RuntimeException expected = new RuntimeException("expected");
        Throwable throwable = proxy.handleOnFailure(context, expected);
        assertThat(throwable).isSameAs(expected);
    }

    @Test
    public void testToInvocationDebugString() throws Exception {
        Throwable cause = new RuntimeException("cause");
        InvocationEventProxy.logInvocationWarning("test", this, getStringLengthMethod(), cause);
    }

    @Test
    public void testToInvocationContextDebugString() throws Exception {
        Throwable cause = new RuntimeException("cause");
        InvocationContext context = DefaultInvocationContext.of("test", getStringLengthMethod(), EMPTY_ARGS);
        InvocationEventProxy.logInvocationWarning("test", context, 13, cause);
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

        assertThat(proxy).asString().isEqualTo("Hello, world");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentInvocation() throws Throwable {
        InvocationEventProxy proxy = createTestProxy(new SimpleHandler());

        assertThat(proxy.invoke("test", getStringLengthMethod(), null)).isEqualTo("test".length());
        assertThat(proxy.invoke("test", getStringLengthMethod(), EMPTY_ARGS)).isEqualTo("test".length());
        assertThatThrownBy(() -> proxy.invoke("test", getStringLengthMethod(), new Object[] {"Hello"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("wrong number of arguments");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentInvocationThrowsException() {
        InvocationEventProxy proxy = createSimpleTestProxy();

        assertThatThrownBy(() -> proxy.invoke("test", getThrowsCheckedExceptionMethod(), null))
                .isInstanceOf(TestImplementation.TestException.class)
                .hasMessage("Testing checked Exception handling");

        assertThatThrownBy(() -> proxy.invoke("test", getThrowsThrowableMethod(), null))
                .isInstanceOf(TestImplementation.TestThrowable.class)
                .hasMessage("TestThrowable");

        assertThatThrownBy(() -> proxy.invoke("test", getThrowsOutOfMemoryErrorMethod(), null))
                .isInstanceOf(OutOfMemoryError.class)
                .hasMessage("Testing OOM");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testExecute() throws Throwable {
        InvocationEventProxy proxy = createTestProxy(new SimpleHandler());

        assertThat(proxy.invoke(proxy, getStringLengthMethod(), null)).isEqualTo("test".length());
        assertThat(proxy.invoke(proxy, getStringLengthMethod(), EMPTY_ARGS)).isEqualTo("test".length());
        assertThatThrownBy(() -> proxy.invoke(proxy, getStringLengthMethod(), new Object[] {"Hello"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("wrong number of arguments");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testExecuteThrowsExceptions() {
        InvocationEventProxy proxy = createSimpleTestProxy();

        assertThatThrownBy(() -> proxy.invoke(proxy, getThrowsCheckedExceptionMethod(), null))
                .isInstanceOf(TestImplementation.TestException.class)
                .hasMessage("Testing checked Exception handling");

        assertThatThrownBy(() -> proxy.invoke(proxy, getThrowsThrowableMethod(), null))
                .isInstanceOf(TestImplementation.TestThrowable.class)
                .hasMessage("TestThrowable");

        assertThatThrownBy(() -> proxy.invoke(proxy, getThrowsOutOfMemoryErrorMethod(), null))
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
        assertThat(proxy.invoke(this, getStringLengthMethod(), EMPTY_ARGS)).isEqualTo("test".length());

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
        assertThat(proxy.invoke(this, getStringLengthMethod(), EMPTY_ARGS)).isEqualTo("test".length());

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
        assertThat(proxy.invoke(mockHandler, getStringLengthMethod(), EMPTY_ARGS)).isEqualTo("test".length());

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

    // n.b. cannot use toString because it's special cased
    private static Method getStringLengthMethod() throws NoSuchMethodException {
        return String.class.getDeclaredMethod("length");
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
        public void onSuccess(@Nullable InvocationContext unusedContext, @Nullable Object unusedResult) {}

        @Override
        public void onFailure(@Nullable InvocationContext unusedContext, @Nonnull Throwable unusedCause) {}
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
