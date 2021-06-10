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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class InstrumentedSslEngine extends SSLEngine {

    private static final Logger log = LoggerFactory.getLogger(InstrumentedSslEngine.class);

    // n.b. This value is set using 'beginHandshake' for renegotiation. We instrument both because ciphers may change.
    private final AtomicBoolean handshaking = new AtomicBoolean(true);
    private final SSLEngine engine;
    private final TlsMetrics metrics;
    private final String name;

    /**
     * Instrument the provided {@link SSLEngine}.
     * Note that this assumes up-to-date JVMs, older Java 8 releases did not provide
     * ALPN API methods {@link #getApplicationProtocol()}, {@link #getHandshakeApplicationProtocol()},
     * {@link #setHandshakeApplicationProtocolSelector(BiFunction)}, {@link #getHandshakeApplicationProtocolSelector()}.
     * Using this method on an older jre8 release will not fail outright, but some callers may assume
     * ALPN is supported by this implementation when calls to the delegate fail. Reflection is not
     * used here because it increases risk of incorrect behavior using graalvm native-images.
     *
     * @see <a href="https://openjdk.java.net/jeps/244">JEP 244</a>
     * @see <a href="https://bugs.openjdk.java.net/browse/JDK-8230977">JDK-8230977</a>
     */
    static SSLEngine instrument(SSLEngine engine, TlsMetrics metrics, String name) {
        return new InstrumentedSslEngine(engine, metrics, name);
    }

    // Extracts a delegate SSLEngine instance if the input is wrapped.
    static SSLEngine extractDelegate(SSLEngine maybeInstrumented) {
        SSLEngine current = maybeInstrumented;
        while (current instanceof InstrumentedSslEngine) {
            current = ((InstrumentedSslEngine) current).engine;
        }
        return current;
    }

    private InstrumentedSslEngine(SSLEngine engine, TlsMetrics metrics, String name) {
        this.engine = engine;
        this.metrics = metrics;
        this.name = name;
    }

    @Override
    public String getPeerHost() {
        return engine.getPeerHost();
    }

    @Override
    public int getPeerPort() {
        return engine.getPeerPort();
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
        return check(engine.wrap(src, dst));
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] sources, ByteBuffer byteBuffer) throws SSLException {
        return check(engine.wrap(sources, byteBuffer));
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] sources, int offset, int length, ByteBuffer dest) throws SSLException {
        return check(engine.wrap(sources, offset, length, dest));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer byteBuffer, ByteBuffer byteBuffer1) throws SSLException {
        return check(engine.unwrap(byteBuffer, byteBuffer1));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer byteBuffer, ByteBuffer[] byteBuffers) throws SSLException {
        return check(engine.unwrap(byteBuffer, byteBuffers));
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) throws SSLException {
        return check(engine.unwrap(src, dsts, offset, length));
    }

    @Override
    public Runnable getDelegatedTask() {
        return engine.getDelegatedTask();
    }

    @Override
    public void closeInbound() throws SSLException {
        engine.closeInbound();
    }

    @Override
    public boolean isInboundDone() {
        return engine.isInboundDone();
    }

    @Override
    public void closeOutbound() {
        engine.closeOutbound();
    }

    @Override
    public boolean isOutboundDone() {
        return engine.isOutboundDone();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return engine.getSupportedCipherSuites();
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return engine.getEnabledCipherSuites();
    }

    @Override
    public void setEnabledCipherSuites(String[] strings) {
        engine.setEnabledCipherSuites(strings);
    }

    @Override
    public String[] getSupportedProtocols() {
        return engine.getSupportedProtocols();
    }

    @Override
    public String[] getEnabledProtocols() {
        return engine.getEnabledProtocols();
    }

    @Override
    public void setEnabledProtocols(String[] strings) {
        engine.setEnabledProtocols(strings);
    }

    @Override
    public SSLSession getSession() {
        return engine.getSession();
    }

    @Override
    public SSLSession getHandshakeSession() {
        return engine.getHandshakeSession();
    }

    @Override
    public void beginHandshake() throws SSLException {
        engine.beginHandshake();
        handshaking.set(true);
    }

    @Override
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return engine.getHandshakeStatus();
    }

    @Override
    public void setUseClientMode(boolean mode) {
        engine.setUseClientMode(mode);
    }

    @Override
    public boolean getUseClientMode() {
        return engine.getUseClientMode();
    }

    @Override
    public void setNeedClientAuth(boolean need) {
        engine.setNeedClientAuth(need);
    }

    @Override
    public boolean getNeedClientAuth() {
        return engine.getNeedClientAuth();
    }

    @Override
    public void setWantClientAuth(boolean want) {
        engine.setWantClientAuth(want);
    }

    @Override
    public boolean getWantClientAuth() {
        return engine.getWantClientAuth();
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {
        engine.setEnableSessionCreation(flag);
    }

    @Override
    public boolean getEnableSessionCreation() {
        return engine.getEnableSessionCreation();
    }

    @Override
    public SSLParameters getSSLParameters() {
        return engine.getSSLParameters();
    }

    @Override
    public void setSSLParameters(SSLParameters sslParameters) {
        engine.setSSLParameters(sslParameters);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{name=" + name + ", delegate=" + engine + '}';
    }

    @Override
    public String getApplicationProtocol() {
        return engine.getApplicationProtocol();
    }

    @Override
    public String getHandshakeApplicationProtocol() {
        return engine.getHandshakeApplicationProtocol();
    }

    @Override
    public void setHandshakeApplicationProtocolSelector(BiFunction<SSLEngine, List<String>, String> selector) {
        engine.setHandshakeApplicationProtocolSelector(selector);
    }

    @Override
    @SuppressWarnings("NoFunctionalReturnType")
    public BiFunction<SSLEngine, List<String>, String> getHandshakeApplicationProtocolSelector() {
        return engine.getHandshakeApplicationProtocolSelector();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof InstrumentedSslEngine) {
            InstrumentedSslEngine that = (InstrumentedSslEngine) other;
            return engine.equals(that.engine) && name.equals(that.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(engine, name);
    }

    private SSLEngineResult check(SSLEngineResult result) {
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED
                && handshaking.compareAndSet(/* expectedValue= */ true, /* newValue= */ false)) {
            try {
                SSLSession session = engine.getSession();
                if (session != null) {
                    HandshakeInstrumentation.record(metrics, name, session.getCipherSuite(), session.getProtocol());
                }
            } catch (RuntimeException e) {
                log.warn("Failed to record handshake metrics", e);
            }
        }
        return result;
    }
}
