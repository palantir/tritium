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
import java.io.InputStream;
import java.io.OutputStream;

public final class InstrumentedStreams {
    private InstrumentedStreams() {}

    /**
     * Instruments the provided stream to provide a meter tracking bytes read,
     * and histogram tracking bytes per second.
     *
     * @param in input
     * @param bytes bytes read
     * @param throughput bytes read per second
     * @return instrumented input stream
     */
    public static InputStream input(InputStream in, Meter bytes, Histogram throughput) {
        return new InstrumentedInputStream(in, bytes, throughput);
    }

    /**
     * Instruments the provided stream to provide a meter tracking bytes written,
     * and histogram tracking bytes per second.
     *
     * @param out output
     * @param bytes bytes read
     * @param throughput bytes read per second
     * @return instrumented output stream
     */
    public static OutputStream output(OutputStream out, Meter bytes, Histogram throughput) {
        return new InstrumentedOutputStream(out, bytes, throughput);
    }
}
