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

import com.google.common.collect.ImmutableList;
import com.palantir.tritium.event.DefaultInvocationContext;
import com.palantir.tritium.event.InstrumentationFilters;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.Test;

public class InvocationEventProxyTest {

    private static final Object[] EMPTY_ARGS = {};

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testDisabled() throws Throwable {
        InvocationEventProxy<InvocationContext> proxy = new InvocationEventProxy<InvocationContext>(
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
        InvocationEventProxy<InvocationContext> proxy = createTestProxy(ImmutableList.of(testHandler));

        Object result1 = proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS);

        assertThat(result1).isNotNull();
        assertThat(result1.toString()).isEqualTo("test");

        Object result2 = proxy.handlePreInvocation(this, getToStringMethod(), EMPTY_ARGS);
        assertThat(result2).isNotNull();
        assertThat(result2).isInstanceOf(DefaultInvocationContext.class);
        assertThat(result2.toString()).contains(InvocationEventProxyTest.class.getName());

        InvocationContext context = proxy.handlePreInvocation(this, getToStringMethod(), EMPTY_ARGS);
        assertThat(context).isNotNull();
        assertThat(context.toString()).contains("startTimeNanos");
        assertThat(context.toString()).contains("instance");
        assertThat(context.toString()).contains("method");
        assertThat(context.toString()).contains("args");
    }

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testInstrumentPreInvocationThrows() throws Throwable {
        InvocationEventHandler<InvocationContext> testHandler = new SimpleHandler() {
            @Override
            public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
                throw new IllegalStateException("expected");
            }
        };
        InvocationEventProxy<InvocationContext> proxy = createTestProxy(ImmutableList.of(testHandler));

        Object result = proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS);

        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo("test");
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

        InvocationEventProxy<InvocationContext> proxy = createTestProxy(ImmutableList.of(testHandler));

        Object result = proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS);

        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo("test");

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

        InvocationEventProxy<InvocationContext> proxy = createTestProxy(ImmutableList.of(testHandler));

        Object result = proxy.handleInvocation(this, getToStringMethod(), EMPTY_ARGS);

        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo("test");

        InvocationContext context = DefaultInvocationContext.of(this, getToStringMethod(), null);
        RuntimeException expected = new RuntimeException("expected");
        Throwable throwable = proxy.handleOnFailure(context, expected);
        assertThat(throwable).isSameAs(expected);
    }

    @Test
    public void testInstrumentCompletableFuture() {

    }

    @Test
    public void testToInvocationDebugString() throws Exception {
        Throwable cause = new RuntimeException("cause");
        InvocationEventProxy.logInvocationWarning("test", this, getToStringMethod(), null, cause);
    }

    @Test
    public void testInstrumentToString() {
        List<InvocationEventHandler<InvocationContext>> handlers = Collections.emptyList();
        InvocationEventProxy<InvocationContext> proxy = new InvocationEventProxy<InvocationContext>(handlers) {
            @Override
            Object getDelegate() {
                return "Hello, world";
            }
        };

        assertThat(proxy.toString()).isEqualTo("Hello, world");
    }

    private static InvocationEventProxy<InvocationContext> createTestProxy(
            List<InvocationEventHandler<InvocationContext>> handlers) {
        return new InvocationEventProxy<InvocationContext>(handlers) {
            @Override
            Object getDelegate() {
                return "test";
            }
        };
    }

    private static Method getToStringMethod() throws NoSuchMethodException {
        return Object.class.getDeclaredMethod("toString");
    }

    private static class SimpleHandler implements InvocationEventHandler<InvocationContext> {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
            return DefaultInvocationContext.of(instance, method, args);
        }

        @Override
        public void onSuccess(@Nullable InvocationContext context, @Nullable Object result) {}

        @Override
        public void onFailure(@Nullable InvocationContext context, @Nonnull Throwable cause) {}
    }
}
