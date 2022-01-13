/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.palantir.tritium.Tagged;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class InstrumentedStreamsTest {

    @Test
    void instrumentedCopy() throws IOException {
        byte[] bytes = new byte[100 * 1024 * 1024];
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        IoStreamMetrics metrics = IoStreamMetrics.of(registry);

        int iterations = 10;
        for (long i = 1; i <= iterations; i++) {
            try (ConsoleReporter reporter =
                            ConsoleReporter.forRegistry(new MetricRegistry()).build();
                    InputStream input = new ByteArrayInputStream(bytes);
                    OutputStream output = new ByteArrayOutputStream(bytes.length);
                    InputStream instrumentedInputStream = InstrumentedStreams.input(input, registry, "test-in");
                    OutputStream instrumentedOutputStream = InstrumentedStreams.output(output, registry, "test-out")) {
                assertThat(ByteStreams.copy(instrumentedInputStream, instrumentedOutputStream))
                        .isEqualTo(bytes.length);
                assertThat(metrics.read("test-in").getCount()).isNotZero().isEqualTo(i * bytes.length);
                assertThat(metrics.write("test-out").getCount()).isNotZero().isEqualTo(i * bytes.length);
                Tagged.report(reporter, registry);
            }
        }

        assertThat(metrics.read("test-in").getCount()).isNotZero().isEqualTo(iterations * bytes.length);
        assertThat(metrics.write("test-out").getCount()).isNotZero().isEqualTo(iterations * bytes.length);
    }

    @Test
    void instrumentedGzip() throws IOException {
        byte[] bytes = new byte[1024];
        ThreadLocalRandom.current().nextBytes(bytes);
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        IoStreamMetrics metrics = IoStreamMetrics.of(registry);
        long totalSize = 1L << 31;
        ByteSource byteSource = ByteSource.concat(Iterables.cycle(ByteSource.wrap(bytes)));

        try (ConsoleReporter reporter =
                        ConsoleReporter.forRegistry(new MetricRegistry()).build();
                InputStream input = ByteStreams.limit(byteSource.openStream(), totalSize);
                InputStream instrumentedInputStream = InstrumentedStreams.input(input, registry, "test-in");
                OutputStream output = ByteStreams.nullOutputStream();
                OutputStream instrumentedRawOutputStream = InstrumentedStreams.output(output, registry, "raw-out");
                OutputStream gzipOut = new GZIPOutputStream(instrumentedRawOutputStream);
                OutputStream instrumentedGzipOutputStream = InstrumentedStreams.output(gzipOut, registry, "gzip-out")) {
            assertThat(ByteStreams.copy(instrumentedInputStream, instrumentedGzipOutputStream))
                    .isEqualTo(totalSize);
            assertThat(metrics.read("test-in").getCount()).isNotZero().isEqualTo(totalSize);
            assertThat(metrics.write("gzip-out").getCount())
                    .isNotZero()
                    .isEqualTo(totalSize)
                    .isGreaterThan(metrics.write("raw-out").getCount());
            assertThat(metrics.write("raw-out").getCount()).isNotZero();
            Tagged.report(reporter, registry);
        }
    }
}
