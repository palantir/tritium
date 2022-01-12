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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

abstract class ForwardingInputStream extends FilterInputStream {
    private static final SafeLogger log = SafeLoggerFactory.get(ForwardingInputStream.class);

    protected ForwardingInputStream(InputStream in) {
        super(in);
    }

    protected final InputStream input() {
        return super.in;
    }

    /**
     * Hook to handle number of bytes that will attempt to read.
     * Default implementation logs the number of bytes that will attempt to read.
     * @param bytesToRead number of bytes that will attempt to read
     */
    protected void before(long bytesToRead) {
        if (log.isTraceEnabled()) {
            log.trace("Attempting to read", SafeArg.of("bytesToRead", bytesToRead));
        }
    }

    /**
     * Hook to handle number of bytes that were actually read, or -1 if end-of-stream.
     * Default implementation logs the number of bytes read.
     * @param bytesRead number of bytes that were read, or -1 if end-of-stream
     */
    protected void after(long bytesRead) {
        if (log.isDebugEnabled()) {
            log.debug("Read", SafeArg.of("bytesRead", bytesRead));
        }
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        Objects.checkFromIndexSize(off, len, bytes.length);
        before(len);
        int bytesRead = input().read(bytes, off, len);
        after(bytesRead);
        return bytesRead;
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        before(bytes.length);
        int bytesRead = input().read(bytes);
        after(bytesRead);
        return bytesRead;
    }

    @Override
    public int read() throws IOException {
        before(1);
        int bytesRead = input().read();
        after(bytesRead);
        return bytesRead;
    }

    @Override
    public int readNBytes(byte[] bytes, int off, int len) throws IOException {
        before(len);
        int bytesRead = input().readNBytes(bytes, off, len);
        after(bytesRead);
        return bytesRead;
    }
}
