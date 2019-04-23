/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.tritium.metrics.tls;

import com.google.common.annotations.Beta;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;

/** TODO(ckozak): docs. */
@Beta
public final class TlsMetrics {

    /** TODO(ckozak): docs. */
    public static SSLContext instrument(TaggedMetricRegistry registry, SSLContext context, String name) {
        return new InstrumentedSslContext(context, registry, name);
    }

    /** TODO(ckozak): docs. */
    public static SSLSocketFactory instrument(TaggedMetricRegistry registry, SSLSocketFactory factory, String name) {
        return new InstrumentedSslSocketFactory(factory, registry, name);
    }

    /** TODO(ckozak): docs. */
    public static SSLServerSocketFactory instrument(
            TaggedMetricRegistry registry, SSLServerSocketFactory factory, String name) {
        return new InstrumentedSslServerSocketFactory(factory, registry, name);
    }

    /** TODO(ckozak): docs. */
    public static SSLEngine instrument(TaggedMetricRegistry registry, SSLEngine engine, String name) {
        return new InstrumentedSslEngine(engine, registry, name);
    }

    private TlsMetrics() {}
}
