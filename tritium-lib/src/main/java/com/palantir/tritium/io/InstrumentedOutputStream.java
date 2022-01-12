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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.palantir.logsafe.Preconditions;
import java.io.OutputStream;

final class InstrumentedOutputStream extends ForwardingOutputStream {
    private final Meter bytes;
    private final Histogram throughput;
    private long start;

    InstrumentedOutputStream(OutputStream in, Meter bytes, Histogram throughput) {
        super(in);
        this.bytes = Preconditions.checkNotNull(bytes, "bytes");
        this.throughput = Preconditions.checkNotNull(throughput, "throughput");
    }

    @Override
    protected void before(long bytesToWrite) {
        start = System.nanoTime();
        super.before(bytesToWrite);
    }

    @Override
    protected void after(long bytesWritten) {
        double elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0;
        long bytesPerSecond = Math.round(bytesWritten / elapsedSeconds);
        throughput.update(bytesPerSecond);
        this.bytes.mark(bytesWritten);
        super.after(bytesWritten);
    }
}
