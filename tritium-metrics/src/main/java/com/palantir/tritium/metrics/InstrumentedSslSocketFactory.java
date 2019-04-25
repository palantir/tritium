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

package com.palantir.tritium.metrics;

import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

final class InstrumentedSslSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory delegate;
    private final HandshakeCompletedListener listener;

    InstrumentedSslSocketFactory(SSLSocketFactory delegate, TaggedMetricRegistry metrics, String name) {
        this.delegate = delegate;
        this.listener = newHandshakeListener(metrics, name);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return wrap(delegate.createSocket(socket, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return wrap(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress inetAddress, int clientPort)
            throws IOException {
        return wrap(delegate.createSocket(host, port, inetAddress, clientPort));
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int port) throws IOException {
        return wrap(delegate.createSocket(inetAddress, port));
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int port, InetAddress clientAddress, int clientPort)
            throws IOException {
        return wrap(delegate.createSocket(inetAddress, port, clientAddress, clientPort));
    }

    @Override
    public Socket createSocket(Socket socket, InputStream consumed, boolean autoClose) throws IOException {
        return wrap(delegate.createSocket(socket, consumed, autoClose));
    }

    @Override
    public Socket createSocket() throws IOException {
        return wrap(delegate.createSocket());
    }

    @Override
    public String toString() {
        return "InstrumentedSSLSocketFactory{delegate=" + delegate + '}';
    }

    private Socket wrap(Socket socket) {
        if (socket instanceof SSLSocket) {
            ((SSLSocket) socket).addHandshakeCompletedListener(listener);
        }
        return socket;
    }

    static HandshakeCompletedListener newHandshakeListener(TaggedMetricRegistry metrics, String name) {
        return event -> metrics.meter(MetricName.builder()
                .safeName("tls.handshake")
                .putSafeTags("name", name)
                .putSafeTags("cipher", event.getCipherSuite())
                .putSafeTags("protocol", event.getSession().getProtocol())
                .build())
                .mark();
    }
}
