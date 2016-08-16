/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
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

        assertThat(compositeHandler, instanceOf(CompositeInvocationEventHandler.class));

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

        assertThat(compositeHandler, instanceOf(CompositeInvocationEventHandler.class));

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
        assertThat(compositeHandler, instanceOf(CompositeInvocationEventHandler.class));

        InvocationContext context = compositeHandler.preInvocation(this, getToStringMethod(), EMPTY_ARGS);
        compositeHandler.onFailure(context, new RuntimeException("simple failure"));
    }

    @Test
    public void testEmpty() throws Exception {
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(
                Collections.emptyList());
        assertThat(compositeHandler, equalTo(NoOpInvocationEventHandler.INSTANCE));
        assertThat(compositeHandler, instanceOf(NoOpInvocationEventHandler.class));

        InvocationContext context = compositeHandler.preInvocation(this, getToStringMethod(), EMPTY_ARGS);

        assertThat(context, not(equalTo(null)));
        assertThat(context.getMethod().getName(), equalTo("toString"));
        compositeHandler.onSuccess(context, "Hello World");
        compositeHandler.onFailure(context, new RuntimeException());
    }

    @Test(expected = NullPointerException.class)
    public void testNullHandler() throws Exception {
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(
                Arrays.asList(null, null, NoOpInvocationEventHandler.INSTANCE));
        assertThat(compositeHandler, instanceOf(CompositeInvocationEventHandler.class));

        compositeHandler.preInvocation(this, getToStringMethod(), EMPTY_ARGS);
        fail("should have thrown");
    }

    @Test
    public void testPreInvocationThrowingHandler() throws Exception {
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(
                Arrays.asList(NoOpInvocationEventHandler.INSTANCE, createThrowingHandler(true)));
        assertThat(compositeHandler, instanceOf(CompositeInvocationEventHandler.class));

        InvocationContext context = compositeHandler.preInvocation(this, getToStringMethod(), EMPTY_ARGS);
        assertThat(context, instanceOf(InvocationContext.class));
    }

    @Test
    public void testThrowingOnSuccess() throws Exception {
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(
                Arrays.asList(NoOpInvocationEventHandler.INSTANCE, createThrowingHandler(true)));
        assertThat(compositeHandler, instanceOf(CompositeInvocationEventHandler.class));

        InvocationContext context = new CompositeInvocationEventHandler.CompositeInvocationContext(this,
                getToStringMethod(), null, new InvocationContext[] {null, null});
        compositeHandler.onSuccess(context, "Hello World");
    }

    @Test
    public void testThrowingOnFailure() throws Exception {
        InvocationEventHandler<InvocationContext> throwingHandler = createThrowingHandler(true);
        InvocationEventHandler<InvocationContext> compositeHandler = CompositeInvocationEventHandler.of(
                Arrays.asList(NoOpInvocationEventHandler.INSTANCE, throwingHandler));
        assertThat(compositeHandler, instanceOf(CompositeInvocationEventHandler.class));

        InvocationContext context = new CompositeInvocationEventHandler.CompositeInvocationContext(this,
                getToStringMethod(), null, new InvocationContext[] {null, null});
        compositeHandler.onFailure(context, new RuntimeException());
    }

    private static Method getToStringMethod() throws NoSuchMethodException {
        return Object.class.getDeclaredMethod("toString");
    }

    private InvocationEventHandler<InvocationContext> createThrowingHandler(final boolean isEnabled) {
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
        public void onFailure(@Nullable InvocationContext context, Throwable cause) {}
    }

}
