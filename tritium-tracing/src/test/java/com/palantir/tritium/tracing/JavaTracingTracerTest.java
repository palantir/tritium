/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.tracing.AlwaysSampler;
import com.palantir.tracing.api.Span;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.Test;

public class JavaTracingTracerTest {

    @Test
    public void trace() {
        Deque<Span> observedSpans = new ConcurrentLinkedDeque<>();
        com.palantir.tracing.Tracer.setSampler(AlwaysSampler.INSTANCE);
        com.palantir.tracing.Tracer.subscribe("test", observedSpans::add);
        JavaTracingTracer tracer = JavaTracingTracer.INSTANCE;
        assertThat(observedSpans).isEmpty();
        tracer.startSpan("test");
        assertThat(observedSpans).isEmpty();
        tracer.completeSpan();
        assertThat(observedSpans)
                .hasSize(1)
                .first()
                .extracting("operation")
                .contains("test");
    }

}
