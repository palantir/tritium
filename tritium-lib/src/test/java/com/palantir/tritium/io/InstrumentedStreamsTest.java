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
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.io.ByteStreams;
import com.palantir.tritium.metrics.MetricRegistries;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.Test;

class InstrumentedStreamsTest {

    @SuppressWarnings("SystemOut")
    @Test
    void copy() throws IOException {
        byte[] bytes = new byte[100 * 1024 * 1024];
        MetricRegistry registry = MetricRegistries.createWithHdrHistogramReservoirs();

        Meter bytesReadMeter = registry.meter("bytes-read");
        Histogram readThroughput = registry.histogram("bytes-read-per-second");

        Meter bytesWrittenMeter = registry.meter("bytes-written");
        Histogram writeThroughput = registry.histogram("bytes-written-per-second");

        int iterations = 10;
        for (long i = 1; i <= iterations; i++) {
            try (ConsoleReporter reporter =
                            ConsoleReporter.forRegistry(registry).build();
                    InputStream input = new ByteArrayInputStream(bytes);
                    OutputStream output = new ByteArrayOutputStream(bytes.length);
                    InputStream instrumentedInputStream =
                            InstrumentedStreams.input(input, bytesReadMeter, readThroughput);
                    OutputStream instrumentedOutputStream =
                            InstrumentedStreams.output(output, bytesWrittenMeter, writeThroughput)) {
                assertThat(ByteStreams.copy(instrumentedInputStream, instrumentedOutputStream))
                        .isEqualTo(bytes.length);
                assertThat(bytesReadMeter.getCount()).isNotZero().isEqualTo(i * bytes.length);
                assertThat(bytesWrittenMeter.getCount()).isNotZero().isEqualTo(i * bytes.length);
                reporter.report();
            }
        }

        assertThat(bytesReadMeter.getCount()).isNotZero().isEqualTo(iterations * bytes.length);
        assertThat(readThroughput.getCount()).isGreaterThanOrEqualTo(iterations);
        assertThat(readThroughput.getSnapshot()).satisfies(snapshot -> {
            assertThat(snapshot.getMin()).isNotZero();
            assertThat(snapshot.getMean()).isNotZero();
            assertThat(snapshot.getMedian()).isNotZero();
            assertThat(snapshot.getMax()).isNotZero();
            System.err.printf("Mean read throughput %.3g GiB/sec %n", snapshot.getMean() / (1024.0 * 1024.0 * 1024.0));
        });

        assertThat(bytesWrittenMeter.getCount()).isNotZero().isEqualTo(iterations * bytes.length);
        assertThat(writeThroughput.getCount()).isGreaterThanOrEqualTo(iterations);
        assertThat(writeThroughput.getSnapshot()).satisfies(snapshot -> {
            assertThat(snapshot.getMin()).isNotZero();
            assertThat(snapshot.getMean()).isNotZero();
            assertThat(snapshot.getMedian()).isNotZero();
            assertThat(snapshot.getMax()).isNotZero();
            System.err.printf("Mean write throughput %.3g GiB/sec %n", snapshot.getMean() / (1024.0 * 1024.0 * 1024.0));
        });
    }
}
