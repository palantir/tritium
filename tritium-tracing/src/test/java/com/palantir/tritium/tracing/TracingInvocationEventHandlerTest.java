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

package com.palantir.tritium.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.ImmutableSet;
import com.palantir.tracing.AlwaysSampler;
import com.palantir.tracing.Tracer;
import com.palantir.tracing.api.Span;
import com.palantir.tracing.api.SpanObserver;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.event.InvocationEventHandler;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NullAway") // mock injection
public class TracingInvocationEventHandlerTest {

    private InvocationEventHandler<InvocationContext> handler;
    private TestInterface instance;
    private Method method;
    private Object[] args;

    @Mock
    private SpanObserver mockSpanObserver;

    @BeforeEach
    @SuppressWarnings("SystemOut") // testing trace output to system out
    public void before() throws Exception {
        Tracer.getAndClearTrace();
        MDC.clear();
        handler = TracingInvocationEventHandler.create("testComponent");
        assertThat(handler).isInstanceOf(TracingInvocationEventHandler.class);
        Tracer.setSampler(AlwaysSampler.INSTANCE);
        Tracer.subscribe("sysout", System.out::println);
        Tracer.subscribe("mock", mockSpanObserver);

        instance = new TestImplementation();
        method = instance.getClass().getDeclaredMethod("bulk", Set.class);
        args = new Object[] {ImmutableSet.of("testArg")};
    }

    @AfterEach
    public void after() {
        Tracer.unsubscribe("sysout");
        Tracer.unsubscribe("mock");
        Tracer.getAndClearTrace();
        MDC.clear();
    }

    @Test
    public void testPreInvocation() {
        long startNanoseconds = System.nanoTime();

        InvocationContext context = handler.preInvocation(instance, method, args);

        assertThat(context).isNotNull();
        assertThat(context.getMethod()).isEqualTo(method);
        assertThat(context.getArgs()).isEqualTo(args);
        assertThat(context.getStartTimeNanos()).isGreaterThan(startNanoseconds);
        assertThat(context.getStartTimeNanos()).isLessThan(System.nanoTime());
        assertThat(Tracer.hasTraceId()).isTrue();
    }

    @Test
    public void testSuccess() {
        InvocationContext context = handler.preInvocation(instance, method, args);

        assertThat(Tracer.hasTraceId()).isTrue();

        handler.onSuccess(context, null);

        ArgumentCaptor<Span> spanCaptor = ArgumentCaptor.forClass(Span.class);
        verify(mockSpanObserver, times(1)).consume(spanCaptor.capture());

        Span span = spanCaptor.getValue();
        assertThat(span.getDurationNanoSeconds()).isGreaterThan(0L);
        assertThat(Tracer.hasTraceId()).isFalse();
    }

    @Test
    public void testFailure() {
        InvocationContext context = handler.preInvocation(instance, method, args);

        assertThat(Tracer.hasTraceId()).isTrue();

        handler.onFailure(context, new RuntimeException("unexpected"));

        ArgumentCaptor<Span> spanCaptor = ArgumentCaptor.forClass(Span.class);
        verify(mockSpanObserver, times(1)).consume(spanCaptor.capture());

        Span span = spanCaptor.getValue();
        assertThat(span.getDurationNanoSeconds()).isGreaterThan(0L);
        assertThat(Tracer.hasTraceId()).isFalse();
    }

    @Test
    public void preInvocationWithoutSampling() {
        handler.preInvocation(instance, method, args);
        verifyNoMoreInteractions(mockSpanObserver);
    }

    @Test
    public void onSuccessWithNullContextShouldNotThrow() {
        handler.onSuccess(null, new Object());

        verifyNoMoreInteractions(mockSpanObserver);
    }

    @Test
    public void onFailureWithNullContextShouldNotThrow() {
        handler.onFailure(null, new RuntimeException("expected"));

        verifyNoMoreInteractions(mockSpanObserver);
    }

    @Test
    public void testSystemPropertySupplier_Handler_Enabled() {
        assertThat(TracingInvocationEventHandler.getEnabledSupplier("test").asBoolean())
                .isTrue();
    }
}
