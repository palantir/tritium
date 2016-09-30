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

package com.palantir.tritium;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.github.kristofa.brave.http.HttpSpanCollector;
import com.palantir.tritium.brave.BraveLocalTracingInvocationEventHandler;
import com.palantir.tritium.proxy.Instrumentation;
import com.twitter.zipkin.gen.Endpoint;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import zipkin.Span;
import zipkin.junit.ZipkinRule;

public class HttpCollectorTest {

    @Rule
    public final ZipkinRule zipkinRule = new ZipkinRule();

    private TestMetricsHandler metrics = new TestMetricsHandler();
    // set flush interval to 0 so that tests can drive flushing explicitly
    private HttpSpanCollector.Config config = HttpSpanCollector.Config.builder()
            .compressionEnabled(true)
            .flushInterval(0)
            .build();

    private String targetZipkinUrl = getTargetZipkinUrl(zipkinRule);
    private HttpSpanCollector collector = HttpSpanCollector.create(targetZipkinUrl, config, metrics);
    private final Service raw = new TestService();
    private final Sampler sampler = Sampler.ALWAYS_SAMPLE;
    private Endpoint endpoint = Endpoint.create(getClass().getSimpleName(), 127 << 4 | 1);

    private final Brave brave = new Brave.Builder(new InheritableServerClientAndLocalSpanState(endpoint))
            .clock(() -> System.currentTimeMillis() * 1000)
            .spanCollector(collector)
            .traceSampler(sampler)
            .build();

    private final Service instrumentedWithBraveHttp = Instrumentation.builder(Service.class, raw)
            .withHandler(new BraveLocalTracingInvocationEventHandler("testComponent", brave))
            .build();

    private final Service doubleInstrumented = Instrumentation.builder(Service.class, instrumentedWithBraveHttp)
            .withHandler(new BraveLocalTracingInvocationEventHandler("testComponent", brave))
            .build();
    private Random random = new Random(42);

    private static String getTargetZipkinUrl(ZipkinRule zipkinRule) {
        String zipkinUrl = System.getProperty("zipkin.url");
        return (zipkinUrl == null) ? zipkinRule.httpUrl() : zipkinUrl;
    }

    @After
    public void tearDown() throws Exception {
        System.out.printf("Zipkin HTTP collector at %s metrics%n  accepted: %d%n  dropped: %d %n",
                targetZipkinUrl, metrics.getAccepted(), metrics.getDropped());
    }

    @Test
    public void testHttpCollector() throws Exception {
        for (int i = 0; i < 1_000; i++) {
            brave.localTracer().startNewSpan("test-Component", "test-Operation");
            doubleInstrumented.echo("test");
            brave.localTracer().finishSpan(random.nextInt(100_000));
        }
        collector.flush();

        List<List<Span>> traces = zipkinRule.getTraces();
    }

    public interface Service {
        String echo(String input);
    }

    private static class TestService implements Service {
        @Override
        public String echo(String input) {
            return input;
        }
    }

    private static class TestMetricsHandler implements SpanCollectorMetricsHandler {
        private final AtomicLong accepted = new AtomicLong();
        private final AtomicLong dropped = new AtomicLong();

        @Override
        public void incrementAcceptedSpans(int quantity) {
            accepted.incrementAndGet();
        }

        @Override
        public void incrementDroppedSpans(int quantity) {
            dropped.incrementAndGet();
        }

        long getAccepted() {
            return accepted.get();
        }

        long getDropped() {
            return dropped.get();
        }
    }

}
