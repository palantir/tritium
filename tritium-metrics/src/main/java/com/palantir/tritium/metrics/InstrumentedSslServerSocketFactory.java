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

import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

final class InstrumentedSslServerSocketFactory extends SSLServerSocketFactory {

    private final SSLServerSocketFactory delegate;
    private final HandshakeCompletedListener listener;

    InstrumentedSslServerSocketFactory(SSLServerSocketFactory delegate, TaggedMetricRegistry metrics, String name) {
        this.delegate = delegate;
        this.listener = InstrumentedSslSocketFactory.newHandshakeListener(metrics, name);
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
    public ServerSocket createServerSocket(int port) throws IOException {
        return wrap(delegate.createServerSocket(port));
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog) throws IOException {
        return wrap(delegate.createServerSocket(port, backlog));
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog, InetAddress inetAddress) throws IOException {
        return wrap(delegate.createServerSocket(port, backlog, inetAddress));
    }

    @Override
    public ServerSocket createServerSocket() throws IOException {
        return wrap(delegate.createServerSocket());
    }

    @Override
    public String toString() {
        return "InstrumentedSSLServerSocketFactory{delegate=" + delegate + '}';
    }

    private ServerSocket wrap(ServerSocket serverSocket) throws IOException {
        if (serverSocket instanceof SSLServerSocket) {
            return new InstrumentedServerSocket((SSLServerSocket) serverSocket, listener);
        }
        return serverSocket;
    }

    private static final class InstrumentedServerSocket extends SSLServerSocket {

        private final SSLServerSocket delegate;
        private final HandshakeCompletedListener listener;

        InstrumentedServerSocket(SSLServerSocket delegate, HandshakeCompletedListener listener) throws IOException {
            this.delegate = delegate;
            this.listener = listener;
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return delegate.getEnabledCipherSuites();
        }

        @Override
        public void setEnabledCipherSuites(String[] strings) {
            delegate.setEnabledCipherSuites(strings);
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public String[] getSupportedProtocols() {
            return delegate.getSupportedProtocols();
        }

        @Override
        public String[] getEnabledProtocols() {
            return delegate.getEnabledProtocols();
        }

        @Override
        public void setEnabledProtocols(String[] strings) {
            delegate.setEnabledProtocols(strings);
        }

        @Override
        public void setNeedClientAuth(boolean need) {
            delegate.setNeedClientAuth(need);
        }

        @Override
        public boolean getNeedClientAuth() {
            return delegate.getNeedClientAuth();
        }

        @Override
        public void setWantClientAuth(boolean want) {
            delegate.setWantClientAuth(want);
        }

        @Override
        public boolean getWantClientAuth() {
            return delegate.getWantClientAuth();
        }

        @Override
        public void setUseClientMode(boolean flag) {
            delegate.setUseClientMode(flag);
        }

        @Override
        public boolean getUseClientMode() {
            return delegate.getUseClientMode();
        }

        @Override
        public void setEnableSessionCreation(boolean flag) {
            delegate.setEnableSessionCreation(flag);
        }

        @Override
        public boolean getEnableSessionCreation() {
            return delegate.getEnableSessionCreation();
        }

        @Override
        public SSLParameters getSSLParameters() {
            return delegate.getSSLParameters();
        }

        @Override
        public void setSSLParameters(SSLParameters sslParameters) {
            delegate.setSSLParameters(sslParameters);
        }

        @Override
        public void bind(SocketAddress endpoint) throws IOException {
            delegate.bind(endpoint);
        }

        @Override
        public void bind(SocketAddress endpoint, int backlog) throws IOException {
            delegate.bind(endpoint, backlog);
        }

        @Override
        public InetAddress getInetAddress() {
            return delegate.getInetAddress();
        }

        @Override
        public int getLocalPort() {
            return delegate.getLocalPort();
        }

        @Override
        public SocketAddress getLocalSocketAddress() {
            return delegate.getLocalSocketAddress();
        }

        @Override
        public Socket accept() throws IOException {
            return wrap(delegate.accept());
        }

        private Socket wrap(Socket socket) {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).addHandshakeCompletedListener(listener);
            }
            return socket;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public ServerSocketChannel getChannel() {
            return delegate.getChannel();
        }

        @Override
        public boolean isBound() {
            return delegate.isBound();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        @SuppressWarnings("UnsynchronizedOverridesSynchronized") // Delegates to a safe implementation
        public void setSoTimeout(int timeout) throws SocketException {
            delegate.setSoTimeout(timeout);
        }

        @Override
        @SuppressWarnings("UnsynchronizedOverridesSynchronized") // Delegates to a safe implementation
        public int getSoTimeout() throws IOException {
            return delegate.getSoTimeout();
        }

        @Override
        public void setReuseAddress(boolean on) throws SocketException {
            delegate.setReuseAddress(on);
        }

        @Override
        public boolean getReuseAddress() throws SocketException {
            return delegate.getReuseAddress();
        }

        @Override
        @SuppressWarnings("UnsynchronizedOverridesSynchronized") // Delegates to a safe implementation
        public void setReceiveBufferSize(int size) throws SocketException {
            delegate.setReceiveBufferSize(size);
        }

        @Override
        @SuppressWarnings("UnsynchronizedOverridesSynchronized") // Delegates to a safe implementation
        public int getReceiveBufferSize() throws SocketException {
            return delegate.getReceiveBufferSize();
        }

        @Override
        public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
            delegate.setPerformancePreferences(connectionTime, latency, bandwidth);
        }

        @Override
        public String toString() {
            return "InstrumentedServerSocket{delegate=" + delegate + '}';
        }
    }
}
