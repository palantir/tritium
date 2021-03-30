/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.metrics;

import com.palantir.logsafe.SafeArg;
import com.palantir.tritium.v1.core.event.InstrumentationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to record handshake metrics. This class exists to allow a single logger to configure handshake logging
 * for both socket factories and ssl engines.
 */
final class HandshakeInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(HandshakeInstrumentation.class);

    static void record(TlsMetrics metrics, String contextName, String cipherSuite, String protocol) {
        metrics.handshake()
                .context(contextName)
                .cipher(cipherSuite)
                .protocol(protocol)
                .build()
                .mark();
        if (log.isDebugEnabled()) {
            log.debug(
                    "TLS Handshake completed for context {}, cipher {}, protocol {}",
                    SafeArg.of("context", contextName),
                    SafeArg.of("cipherSuite", cipherSuite),
                    SafeArg.of("protocol", protocol));
        }
    }

    /**
     * Socket metrics are more expensive than SSLEngine instrumentation due to HandshakeCompletedListener
     * instances running on a new short-lived thread. When no HandshakeCompletedListeners are registered,
     * the thread is completely avoided.
     */
    static boolean isSocketInstrumentationEnabled() {
        return log.isDebugEnabled() || InstrumentationProperties.isSpecificallyEnabled("tls.socket", false);
    }

    private HandshakeInstrumentation() {}
}
