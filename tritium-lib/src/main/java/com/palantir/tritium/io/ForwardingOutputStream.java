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

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

abstract class ForwardingOutputStream extends FilterOutputStream {
    private static final SafeLogger log = SafeLoggerFactory.get(ForwardingOutputStream.class);

    protected ForwardingOutputStream(OutputStream in) {
        super(in);
    }

    protected final OutputStream output() {
        return super.out;
    }

    /**
     * Hook to handle number of bytes that will attempt to write.
     * Default implementation logs the number of bytes that will attempt to write.
     * @param bytesToWrite number of bytes that will attempt to write
     */
    protected void before(long bytesToWrite) {
        if (log.isTraceEnabled()) {
            log.trace("Attempting to write", SafeArg.of("bytesToWrite", bytesToWrite));
        }
    }

    /**
     * Hook to handle number of bytes that were actually written.
     * Default implementation logs the number of bytes written.
     * @param bytesWritten number of bytes that were written
     */
    protected void after(long bytesWritten) {
        if (log.isDebugEnabled()) {
            log.debug("Wrote", SafeArg.of("bytesWritten", bytesWritten));
        }
    }

    @Override
    public void write(byte[] bytes, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, bytes.length);
        before(len);
        output().write(bytes, off, len);
        after(len);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        before(bytes.length);
        output().write(bytes);
        after(bytes.length);
    }

    @Override
    public void write(int value) throws IOException {
        before(1);
        output().write(value);
        after(1);
    }
}
