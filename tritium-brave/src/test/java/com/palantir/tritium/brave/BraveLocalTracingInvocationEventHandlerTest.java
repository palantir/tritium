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

package com.palantir.tritium.brave;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.kristofa.brave.AnnotationSubmitter;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.SpanCollector;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.palantir.tritium.api.event.InvocationContext;
import com.palantir.tritium.test.TestImplementation;
import com.palantir.tritium.test.TestInterface;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BraveLocalTracingInvocationEventHandlerTest {

    @Mock
    private SpanCollector mockSpanCollector;

    @Mock
    private AnnotationSubmitter.Clock mockClock;

    @Mock
    private Sampler mockSampler;

    private Brave brave;
    private BraveLocalTracingInvocationEventHandler handler;
    private TestInterface instance;
    private Method method;
    private Object[] args;

    @Before
    public void before() throws Exception {
        Endpoint endpoint = Endpoint.create("testService", 127 << 4 | 1, 0);
        brave = new Brave.Builder(new InheritableServerClientAndLocalSpanState(endpoint))
                .spanCollector(mockSpanCollector)
                .clock(mockClock)
                .traceSampler(mockSampler)
                .build();
        handler = new BraveLocalTracingInvocationEventHandler("testComponent", brave);
        brave.localSpanThreadBinder().setCurrentSpan(null);

        instance = new TestImplementation();
        method = instance.getClass().getDeclaredMethod("bulk", Set.class);
        args = new Object[] {ImmutableSet.of("testArg")};

        when(mockSampler.isSampled(anyLong())).thenReturn(true);
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
        assertThat(brave.localSpanThreadBinder().getCurrentLocalSpan()).isNotNull();
    }

    @Test
    public void testSuccess() throws Exception {
        InvocationContext context = handler.preInvocation(instance, method, args);

        handler.onSuccess(context, null);

        ArgumentCaptor<Span> spanCaptor = ArgumentCaptor.forClass(Span.class);
        verify(mockSpanCollector, times(1)).collect(spanCaptor.capture());

        Span span = spanCaptor.getValue();
        assertThat(span.getBinary_annotations())
                .named("Binary annotations %s", binaryAnnotationValues(span))
                .hasSize(2);

        FluentIterable<BinaryAnnotation> binaryAnnotations = FluentIterable.from(span.getBinary_annotations());

        assertThat(binaryAnnotations
                .filter(annotationKeyEquals("error")))
                .named("Binary annotations %s", binaryAnnotationValues(span))
                .hasSize(0);

        assertThat(binaryAnnotations
                .filter(annotationKeyEquals("arg0"))
                .transform(ANNOTATION_TO_VALUE).toList())
                .containsExactly("[testArg]");

        assertThat(binaryAnnotations
                .filter(annotationKeyEquals("lc"))
                .transform(ANNOTATION_TO_VALUE).toList())
                .containsExactly("testComponent");
    }

    private FluentIterable<String> binaryAnnotationValues(Span span) {
        return FluentIterable.from(span.getBinary_annotations()).transform(ANNOTATION_TO_STRING);
    }

    @Test
    public void testFailure() throws Exception {
        InvocationContext context = handler.preInvocation(instance, method, args);

        handler.onFailure(context, new RuntimeException("unexpected"));

        ArgumentCaptor<Span> spanCaptor = ArgumentCaptor.forClass(Span.class);
        verify(mockSpanCollector, times(1)).collect(spanCaptor.capture());

        Span span = spanCaptor.getValue();
        assertThat(span.getBinary_annotations()).hasSize(3);
        assertThat(FluentIterable.from(span.getBinary_annotations()).filter(annotationKeyEquals("arg0"))).hasSize(1);
        assertThat(FluentIterable.from(span.getBinary_annotations()).filter(annotationKeyEquals("error"))).hasSize(1);
    }

    @Test
    public void preInvocationWithoutSampling() throws Exception {
        when(mockSampler.isSampled(anyLong())).thenReturn(false);
        handler.preInvocation(instance, method, args);
        verifyNoMoreInteractions(mockSpanCollector);
    }

    @Test
    public void onSuccessWithNullContextShouldNotThrow() {
        handler.onSuccess(null, new Object());
        verifyNoMoreInteractions(mockSpanCollector);
    }

    @Test
    public void onFailureWithNullContextShouldNotThrow() {
        handler.onFailure(null, new RuntimeException("expected"));
        verifyNoMoreInteractions(mockSpanCollector);
    }

    @Test
    public void testSystemPropertySupplier_Handler_Enabled() throws Exception {
        assertThat(BraveLocalTracingInvocationEventHandler.getEnabledSupplier("test").asBoolean()).isTrue();
    }

    private static final Function<BinaryAnnotation, String> ANNOTATION_TO_STRING =
            new Function<BinaryAnnotation, String>() {
                @Override
                public String apply(@Nullable BinaryAnnotation input) {
                    return input == null ? "" : '{' + input.getKey() + ':' + ANNOTATION_TO_VALUE.apply(input) + '}';
                }
            };

    // TODO (davids): Use StandardCharset
    // CHECKSTYLE:OFF
    private static final Charset UTF_8 = Charsets.UTF_8;
    // CHECKSTYLE:ON

    private static final Function<BinaryAnnotation, String> ANNOTATION_TO_VALUE =
            new Function<BinaryAnnotation, String>() {
                @Override
                public String apply(@Nullable BinaryAnnotation input) {
                    return input == null ? "" : new String(input.getValue(), UTF_8);
                }
            };

    private static Predicate<BinaryAnnotation> annotationKeyEquals(final String annotationKey) {
        return new Predicate<BinaryAnnotation>() {
            @Override
            public boolean apply(@Nullable BinaryAnnotation input) {
                return input != null && annotationKey.equals(input.getKey());
            }
        };
    }

}
