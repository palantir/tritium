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

import com.google.errorprone.annotations.CompileTimeConstant;
import com.palantir.logsafe.Safe;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.InputStream;
import java.io.OutputStream;

public final class InstrumentedStreams {
    private InstrumentedStreams() {}

    /**
     * Instruments the provided stream to provide a meter tracking bytes read.
     *
     * @param in input
     * @param metrics metric registry
     * @param type type of stream being instrumented, must be compile-time safe tag
     * @return instrumented input stream
     */
    public static InputStream input(
            InputStream in, TaggedMetricRegistry metrics, @Safe @CompileTimeConstant final String type) {
        return new InstrumentedInputStream(in, IoStreamMetrics.of(metrics).read(type));
    }

    /**
     * Instruments the provided stream to provide a meter tracking bytes written.
     *
     * @param out output
     * @param type type of stream being instrumented, must be compile-time safe tag
     * @return instrumented output stream
     */
    public static OutputStream output(
            OutputStream out, TaggedMetricRegistry metrics, @Safe @CompileTimeConstant final String type) {
        return new InstrumentedOutputStream(out, IoStreamMetrics.of(metrics).write(type));
    }
}
