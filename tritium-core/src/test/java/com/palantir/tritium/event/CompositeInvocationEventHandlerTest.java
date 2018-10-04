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

package com.palantir.tritium.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.tritium.test.event.ThrowingInvocationEventHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.Test;

public class CompositeInvocationEventHandlerTest {

    private static final Object[] EMPTY_ARGS = {};

    @Test
    @SuppressWarnings("checkstyle:illegalthrows")
    public void testSimpleFlow() throws Throwable {
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(
                Arrays.asList(NoOpInvocationEventHandler.INSTANCE,
                        new SimpleInvocationEventHandler()));

        assertThat(compositeHandler).isInstanceOf(CompositeInvocationEventHandler.class);

        InvocationContext context = compositeHandler.preInvocation(this, getToStringMethod(), EMPTY_ARGS);
        compositeHandler.onSuccess(context, "test");
    }

    @Test
    public void testSuccessHandlerFailureShouldNotThrow() throws Exception {
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(
                Arrays.asList(NoOpInvocationEventHandler.INSTANCE, new ThrowingInvocationEventHandler(true) {
                    @Override
                    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
                        return DefaultInvocationContext.of(instance, method, args);
                    }
                }));

        assertThat(compositeHandler).isInstanceOf(CompositeInvocationEventHandler.class);

        InvocationContext context = compositeHandler.preInvocation(this, getToStringMethod(), EMPTY_ARGS);
        compositeHandler.onSuccess(context, "test");
    }

    @Test
    public void testFailureHandlerFailureShouldNotThrow() throws Exception {
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(
                Arrays.asList(NoOpInvocationEventHandler.INSTANCE, new ThrowingInvocationEventHandler(true) {
                    @Override
                    public InvocationContext preInvocation(Object instance, Method method, Object[] args) {
                        return DefaultInvocationContext.of(instance, method, args);
                    }
                }));
        assertThat(compositeHandler).isInstanceOf(CompositeInvocationEventHandler.class);

        InvocationContext context = compositeHandler.preInvocation(this, getToStringMethod(), EMPTY_ARGS);
        compositeHandler.onFailure(context, new RuntimeException("simple failure"));
    }

    @Test
    public void testEmpty() throws Exception {
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(
                Collections.emptyList());
        assertThat(compositeHandler).isInstanceOf(NoOpInvocationEventHandler.class);
        assertThat(compositeHandler).isSameAs(NoOpInvocationEventHandler.INSTANCE);

        InvocationContext context = compositeHandler.preInvocation(this, getToStringMethod(), EMPTY_ARGS);

        assertThat(context).isNotNull();
        assertThat(context.getMethod().getName()).isEqualTo("toString");
        compositeHandler.onSuccess(context, "Hello World");
        compositeHandler.onFailure(context, new RuntimeException());
    }

    @Test
    public void testNullHandler() {
        assertThatThrownBy(() -> CompositeInvocationEventHandler.of(Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("at index 0");
        assertThatThrownBy(
                () -> CompositeInvocationEventHandler.of(Arrays.asList(NoOpInvocationEventHandler.INSTANCE, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("at index 1");
        assertThatThrownBy(
                () -> CompositeInvocationEventHandler.of(
                        Arrays.asList(null, NoOpInvocationEventHandler.INSTANCE, null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("at index 0");
    }

    @Test
    public void testPreInvocationThrowingHandler() throws Exception {
        @SuppressWarnings("unchecked") List<InvocationEventHandler<InvocationContext>> handlers =
                Arrays.asList(NoOpInvocationEventHandler.INSTANCE, createThrowingHandler(true));
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(handlers);
        assertThat(compositeHandler).isInstanceOf(CompositeInvocationEventHandler.class);

        InvocationContext context = compositeHandler.preInvocation(this, getToStringMethod(), EMPTY_ARGS);
        assertThat(context).isInstanceOf(InvocationContext.class);
    }

    @Test
    public void testThrowingOnSuccess() throws Exception {
        @SuppressWarnings("unchecked") List<InvocationEventHandler<InvocationContext>> handlers =
                Arrays.asList(NoOpInvocationEventHandler.INSTANCE, createThrowingHandler(true));
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(handlers);
        assertThat(compositeHandler).isInstanceOf(CompositeInvocationEventHandler.class);

        InvocationContext context = new CompositeInvocationEventHandler.CompositeInvocationContext(this,
                getToStringMethod(), null, new InvocationContext[] {null, null});
        compositeHandler.onSuccess(context, "Hello World");
    }

    @Test
    public void testThrowingOnFailure() throws Exception {
        InvocationEventHandler<InvocationContext> throwingHandler = createThrowingHandler(true);
        @SuppressWarnings("unchecked") List<InvocationEventHandler<InvocationContext>> handlers =
                Arrays.asList(NoOpInvocationEventHandler.INSTANCE, throwingHandler);
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(handlers);
        assertThat(compositeHandler).isInstanceOf(CompositeInvocationEventHandler.class);

        InvocationContext context = new CompositeInvocationEventHandler.CompositeInvocationContext(this,
                getToStringMethod(), null, new InvocationContext[] {null, null});
        compositeHandler.onFailure(context, new RuntimeException());
    }

    @Test
    public void testToString() {
        InvocationEventHandler<InvocationContext> handler = CompositeInvocationEventHandler.of(
                Arrays.asList(NoOpInvocationEventHandler.INSTANCE, new SimpleInvocationEventHandler()));
        assertThat(handler.toString())
                .startsWith("CompositeInvocationEventHandler{handlers=[INSTANCE, ")
                .endsWith("]}");
    }

    private static Method getToStringMethod() throws NoSuchMethodException {
        return Object.class.getDeclaredMethod("toString");
    }

    private static InvocationEventHandler<InvocationContext> createThrowingHandler(boolean isEnabled) {
        return new ThrowingInvocationEventHandler(isEnabled);
    }

    private static class SimpleInvocationEventHandler extends AbstractInvocationEventHandler<InvocationContext> {
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
