/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.event.log;

/**
 * Logging level to avoid dependency on log4j or other slf4j implementations.
 * @deprecated Use {@link com.palantir.tritium.v1.slf4j.event.LoggingLevel}
 */
@Deprecated // remove post 1.0
public enum LoggingLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR;

    /**
     * Adapts to a {@link com.palantir.tritium.v1.slf4j.event.LoggingLevel} v1 compatible log level.
     * @deprecated use {@link com.palantir.tritium.v1.slf4j.event.LoggingLevel}
     */
    @Deprecated // remove post 1.0
    public com.palantir.tritium.v1.slf4j.event.LoggingLevel asV1() {
        return com.palantir.tritium.v1.slf4j.event.LoggingLevel.valueOf(name());
    }
}
