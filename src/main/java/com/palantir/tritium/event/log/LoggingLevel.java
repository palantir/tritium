/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.tritium.event.log;

/**
 * Logging level to avoid dependency on log4j or other slf4j implementations.
 */
public enum LoggingLevel {
    TRACE, DEBUG, INFO, WARN, ERROR
}
