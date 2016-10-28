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

package com.palantir.tritium.tracing;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.remoting1.tracing.AsyncSlf4jSpanObserver;
import com.palantir.remoting1.tracing.Span;
import com.palantir.remoting1.tracing.SpanObserver;
import com.palantir.remoting1.tracing.Tracer;
import com.palantir.tritium.event.InvocationContext;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class TracingInvocationEventHandlerTest {
    private static final Logger log = LoggerFactory.getLogger(TracingInvocationEventHandlerTest.class);

    private TracingInvocationEventHandler handler;
    private TestInterface instance;
    private Method method;
    private Object[] args;
    private ExecutorService executor;

    @Mock
    private SpanObserver mockSpanObserver;

    @Before
    public void before() throws Exception {
        executor = MoreExecutors.newDirectExecutorService();
        handler = new TracingInvocationEventHandler("testComponent");
        Tracer.subscribe("sysout", new SpanObserver() {
            @Override
            public void consume(com.palantir.remoting1.tracing.Span span) {
                System.out.println(span);
            }
        });
        Tracer.subscribe("mock", mockSpanObserver);
        Tracer.subscribe("slf4j", AsyncSlf4jSpanObserver.of("test", executor));

        instance = new TestImplementation();
        method = instance.getClass().getDeclaredMethod("bulk", Set.class);
        args = new Object[] {ImmutableSet.of("testArg")};
    }

    @After
    public void tearDown() throws Exception {
        Tracer.unsubscribe("sysout");
        Tracer.unsubscribe("mock");
        Tracer.unsubscribe("slf4j");
        executor.shutdownNow();
    }

    @Test
    public void testPreInvocation() throws Exception {
        long startNanoseconds = System.nanoTime();

        InvocationContext context = handler.preInvocation(instance, method, args);

        assertThat(context).isNotNull();
        assertThat(context.getMethod()).isEqualTo(method);
        assertThat(context.getArgs()).isEqualTo(args);
        assertThat(context.getStartTimeNanos()).isGreaterThan(startNanoseconds);
        assertThat(context.getStartTimeNanos()).isLessThan(System.nanoTime());
    }

    @Test
    public void testSuccess() throws Exception {
        InvocationContext context = handler.preInvocation(instance, method, args);

        handler.onSuccess(context, null);

        ArgumentCaptor<Span> spanCaptor = ArgumentCaptor.forClass(Span.class);
        verify(mockSpanObserver, times(1)).consume(spanCaptor.capture());

        Span span = spanCaptor.getValue();
        assertThat(span.getDurationNanoSeconds()).isGreaterThan(0L);
    }

    @Test
    public void testFailure() throws Exception {
        InvocationContext context = handler.preInvocation(instance, method, args);

        handler.onFailure(context, new RuntimeException("unexpected"));

        ArgumentCaptor<Span> spanCaptor = ArgumentCaptor.forClass(Span.class);
        verify(mockSpanObserver, times(1)).consume(spanCaptor.capture());

        Span span = spanCaptor.getValue();
        assertThat(span.getDurationNanoSeconds()).isGreaterThan(0L);
    }

    @Test
    public void preInvocationWithoutSampling() throws Exception {
        handler.preInvocation(instance, method, args);
        verifyNoMoreInteractions(mockSpanObserver);
    }

    @Test
    public void onSuccessWithNullContextShouldNotThrow() {
        handler.onSuccess(null, new Object());

        ArgumentCaptor<Span> spanCaptor = ArgumentCaptor.forClass(Span.class);
        verify(mockSpanObserver, times(1)).consume(spanCaptor.capture());
        Span span = spanCaptor.getValue();
        assertThat(span.getTraceId()).isNotNull();
        verifyNoMoreInteractions(mockSpanObserver);
    }

    @Test
    public void onFailureWithNullContextShouldNotThrow() {
        handler.onFailure(null, new RuntimeException("expected"));

        ArgumentCaptor<Span> spanCaptor = ArgumentCaptor.forClass(Span.class);
        verify(mockSpanObserver, times(1)).consume(spanCaptor.capture());
        Span span = spanCaptor.getValue();
        assertThat(span.getTraceId()).isNotNull();
        verifyNoMoreInteractions(mockSpanObserver);
    }

    @Test
    public void testSystemPropertySupplier_Handler_Enabled() throws Exception {
        assertThat(TracingInvocationEventHandler.getEnabledSupplier("test").asBoolean()).isTrue();
    }

}
