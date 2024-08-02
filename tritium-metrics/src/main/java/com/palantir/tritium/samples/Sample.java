/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.samples;

import java.util.Objects;

public final class Sample {
    private final long value;
    private final long timestamp;
    private final String traceId;

    private Sample(long value, long timestamp, String traceId) {
        this.value = value;
        this.timestamp = timestamp;
        this.traceId = traceId;
    }

    public static Sample of(long value, long timestamp, String traceId) {
        return new Sample(value, timestamp, traceId);
    }

    public long getValue() {
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTraceId() {
        return traceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sample sample = (Sample) o;
        return value == sample.value && timestamp == sample.timestamp && Objects.equals(traceId, sample.traceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, timestamp, traceId);
    }

    @Override
    public String toString() {
        return "Sample{" +
                "id=" + value +
                ", timestamp=" + timestamp +
                ", data='" + traceId + '\'' +
                '}';
    }
}
