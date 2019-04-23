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

import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.security.KeyManagementException;
import java.security.SecureRandom;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

final class InstrumentedSslContext extends SSLContext {

    private final SSLContext context;
    private final String name;

    InstrumentedSslContext(SSLContext context, TaggedMetricRegistry metrics, String name) {
        super(new InstrumentedSslContextSpi(context, metrics, name), context.getProvider(), context.getProtocol());
        this.context = context;
        this.name = name;
    }

    @Override
    public String toString() {
        return "InstrumentedSSLContext{delegate=" + context + ", name=" + name + '}';
    }

    private static final class InstrumentedSslContextSpi extends SSLContextSpi {

        private final SSLContext context;
        private final TaggedMetricRegistry metrics;
        private final String name;

        InstrumentedSslContextSpi(
                // This must be the delegate context, passing an InstrumentedSSLContext
                // will result in infinite recursion.
                SSLContext context,
                TaggedMetricRegistry metrics,
                String name) {
            this.context = context;
            this.metrics = metrics;
            this.name = name;
        }

        @Override
        protected void engineInit(
                KeyManager[] keyManagers,
                TrustManager[] trustManagers,
                SecureRandom secureRandom) throws KeyManagementException {
            context.init(keyManagers, trustManagers, secureRandom);
        }

        @Override
        protected SSLSocketFactory engineGetSocketFactory() {
            return new InstrumentedSslSocketFactory(context.getSocketFactory(), metrics, name);
        }

        @Override
        protected SSLServerSocketFactory engineGetServerSocketFactory() {
            return new InstrumentedSslServerSocketFactory(context.getServerSocketFactory(), metrics, name);
        }

        @Override
        protected SSLEngine engineCreateSSLEngine() {
            return InstrumentedSslEngine.instrument(context.createSSLEngine(), metrics, name);
        }

        @Override
        protected SSLEngine engineCreateSSLEngine(String host, int port) {
            return InstrumentedSslEngine.instrument(context.createSSLEngine(host, port), metrics, name);
        }

        @Override
        protected SSLSessionContext engineGetServerSessionContext() {
            return context.getServerSessionContext();
        }

        @Override
        protected SSLSessionContext engineGetClientSessionContext() {
            return context.getClientSessionContext();
        }

        @Override
        protected SSLParameters engineGetDefaultSSLParameters() {
            return context.getDefaultSSLParameters();
        }

        @Override
        protected SSLParameters engineGetSupportedSSLParameters() {
            return context.getSupportedSSLParameters();
        }

        @Override
        public String toString() {
            return "InstrumentedSSLContextSpi{delegate=" + context + ", name=" + name + '}';
        }
    }
}
