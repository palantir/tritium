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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.google.common.collect.MoreCollectors;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.handlers.ResponseCodeHandler;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.xnio.Options;
import org.xnio.Sequence;

final class InstrumentedSslContextTest {

    private static final String ENABLED_CIPHER = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";
    private static final String ENABLED_PROTOCOL = "TLSv1.2";
    private static final boolean IS_JAVA_8 = System.getProperty("java.version").startsWith("1.8");

    private static final int PORT = 4483;

    @Test
    void testClientInstrumentationHttpsUrlConnection() throws Exception {
        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        try (Closeable ignored = server(newServerContext())) {
            SSLContext context = MetricRegistries.instrument(metrics, newClientContext(), "client-context");
            HttpsURLConnection con = (HttpsURLConnection) new URL("https://localhost:" + PORT).openConnection();
            con.setSSLSocketFactory(context.getSocketFactory());
            assertThat(con.getResponseCode()).isEqualTo(200);
        }

        MetricName name = findName(
                metrics,
                MetricName.builder()
                        .safeName("tls.handshake")
                        .putSafeTags("context", "client-context")
                        .putSafeTags("cipher", ENABLED_CIPHER)
                        .putSafeTags("protocol", ENABLED_PROTOCOL)
                        .build());
        assertThat(metrics.getMetrics()).containsOnlyKeys(name);
        assertThat(metrics.meter(name).getCount()).isOne();
    }

    @Test
    void testClientInstrumentationOkHttp() throws Exception {
        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        SSLSocketFactory socketFactory =
                MetricRegistries.instrument(metrics, newClientContext().getSocketFactory(), "okhttp-client");
        OkHttpClient client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .sslSocketFactory(socketFactory, newTrustManager())
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .build();
        try (Closeable ignored = server(newServerContext());
                Response response = client.newCall(new Request.Builder()
                                .url("https://localhost:" + PORT)
                                .get()
                                .build())
                        .execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.protocol()).isEqualTo(Protocol.HTTP_1_1);
        }

        MetricName name = findName(
                metrics,
                MetricName.builder()
                        .safeName("tls.handshake")
                        .putSafeTags("context", "okhttp-client")
                        .putSafeTags("cipher", ENABLED_CIPHER)
                        .putSafeTags("protocol", ENABLED_PROTOCOL)
                        .build());
        assertThat(metrics.getMetrics()).containsOnlyKeys(name);
        assertThat(metrics.meter(name).getCount()).isOne();
    }

    @Test
    void testClientInstrumentationOkHttpHttp2() throws Exception {
        assumeThat(IS_JAVA_8)
                .describedAs("Java 8 does not support ALPN without additional help")
                .isFalse();
        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        SSLSocketFactory socketFactory =
                MetricRegistries.instrument(metrics, newClientContext().getSocketFactory(), "okhttp-client");
        OkHttpClient client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .sslSocketFactory(socketFactory, newTrustManager())
                .build();
        try (Closeable ignored = server(newServerContext());
                Response response = client.newCall(new Request.Builder()
                                .url("https://localhost:" + PORT)
                                .get()
                                .build())
                        .execute()) {
            assertThat(response.code()).isEqualTo(200);
            // If http/2 does not work on java 9+ we have not properly implemented java 9 ALPN components properly
            assertThat(response.protocol()).isEqualTo(Protocol.HTTP_2);
        }

        MetricName name = findName(
                metrics,
                MetricName.builder()
                        .safeName("tls.handshake")
                        .putSafeTags("context", "okhttp-client")
                        .putSafeTags("cipher", ENABLED_CIPHER)
                        .putSafeTags("protocol", ENABLED_PROTOCOL)
                        .build());
        assertThat(metrics.getMetrics()).containsOnlyKeys(name);
        assertThat(metrics.meter(name).getCount()).isOne();
    }

    @Test
    void testServerInstrumentationHttp2() throws Exception {
        assumeThat(IS_JAVA_8)
                .describedAs("Java 8 does not support ALPN without additional help")
                .isFalse();
        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        OkHttpClient client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .sslSocketFactory(newClientContext().getSocketFactory(), newTrustManager())
                .build();
        try (Closeable ignored = server(MetricRegistries.instrument(metrics, newServerContext(), "h2-server"));
                Response response = client.newCall(new Request.Builder()
                                .url("https://localhost:" + PORT)
                                .get()
                                .build())
                        .execute()) {
            assertThat(response.code()).isEqualTo(200);
            // If http/2 does not work on java 9+ we have not properly implemented java 9 ALPN components properly
            assertThat(response.protocol()).isEqualTo(Protocol.HTTP_2);
        }

        MetricName name = findName(
                metrics,
                MetricName.builder()
                        .safeName("tls.handshake")
                        .putSafeTags("context", "h2-server")
                        .putSafeTags("cipher", ENABLED_CIPHER)
                        .putSafeTags("protocol", ENABLED_PROTOCOL)
                        .build());
        assertThat(metrics.getMetrics()).containsOnlyKeys(name);
        assertThat(metrics.meter(name).getCount()).isOne();
    }

    @Test
    void testServerInstrumentation() throws Exception {
        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        try (Closeable ignored = server(MetricRegistries.instrument(metrics, newServerContext(), "server-context"))) {
            HttpsURLConnection con = (HttpsURLConnection) new URL("https://localhost:" + PORT).openConnection();
            con.setSSLSocketFactory(newClientContext().getSocketFactory());
            assertThat(con.getResponseCode()).isEqualTo(200);
        }

        MetricName name = findName(
                metrics,
                MetricName.builder()
                        .safeName("tls.handshake")
                        .putSafeTags("context", "server-context")
                        .putSafeTags("cipher", ENABLED_CIPHER)
                        .putSafeTags("protocol", ENABLED_PROTOCOL)
                        .build());
        assertThat(metrics.getMetrics()).containsOnlyKeys(name);
        assertThat(metrics.meter(name).getCount()).isOne();
    }

    @Test
    void testSslEngineUnwrapNotInstrumented() throws IOException, GeneralSecurityException {
        SSLEngine engine = newServerContext().createSSLEngine();
        assertThat(MetricRegistries.unwrap(engine)).isSameAs(engine);
    }

    @Test
    void testSslEngineUnwrapInstrumentedTwice() throws IOException, GeneralSecurityException {
        SSLContext context = newServerContext();
        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        SSLContext wrapped =
                MetricRegistries.instrument(metrics, MetricRegistries.instrument(metrics, context, "first"), "second");
        SSLEngine engine = wrapped.createSSLEngine();
        assertThat(engine).isInstanceOf(InstrumentedSslEngine.class);
        assertThat(MetricRegistries.unwrap(engine)).isNotNull().isNotInstanceOf(InstrumentedSslEngine.class);
    }

    @Test
    void testSslContext_equality() throws IOException, GeneralSecurityException {
        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        SSLContext context = newClientContext();
        SSLContext instrumentedFirst = MetricRegistries.instrument(metrics, context, "factory");
        SSLContext instrumentedSecond = MetricRegistries.instrument(metrics, context, "factory");
        SSLContext instrumentedThirdDifferentName = MetricRegistries.instrument(metrics, context, "other");
        assertThat(instrumentedFirst).isEqualTo(instrumentedSecond).isNotEqualTo(instrumentedThirdDifferentName);
        assertThat(instrumentedFirst).hasSameHashCodeAs(instrumentedSecond);
        assertThat(instrumentedFirst.hashCode()).isNotEqualTo(instrumentedThirdDifferentName.hashCode());
    }

    @Test
    void testSslSocketFactory_equality() throws IOException, GeneralSecurityException {
        TaggedMetricRegistry metrics = new DefaultTaggedMetricRegistry();
        SSLSocketFactory socketFactory = newClientContext().getSocketFactory();
        SSLSocketFactory instrumentedFirst = MetricRegistries.instrument(metrics, socketFactory, "factory");
        SSLSocketFactory instrumentedSecond = MetricRegistries.instrument(metrics, socketFactory, "factory");
        SSLSocketFactory instrumentedThirdDifferentName = MetricRegistries.instrument(metrics, socketFactory, "other");
        assertThat(instrumentedFirst).isEqualTo(instrumentedSecond).isNotEqualTo(instrumentedThirdDifferentName);
        assertThat(instrumentedFirst).hasSameHashCodeAs(instrumentedSecond);
        assertThat(instrumentedFirst.hashCode()).isNotEqualTo(instrumentedThirdDifferentName.hashCode());
    }

    private static Closeable server(SSLContext context) {
        Undertow server = Undertow.builder()
                .addHttpsListener(PORT, "0.0.0.0", context)
                .setWorkerThreads(1)
                .setIoThreads(1)
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 1000)
                .setSocketOption(Options.SSL_ENABLED_CIPHER_SUITES, Sequence.of(ENABLED_CIPHER))
                .setSocketOption(Options.SSL_ENABLED_PROTOCOLS, Sequence.of(ENABLED_PROTOCOL))
                .setHandler(ResponseCodeHandler.HANDLE_200) // Always responds 200
                .build();
        server.start();
        return server::stop;
    }

    private static SSLContext newClientContext() throws IOException, GeneralSecurityException {
        return newSslContext("client_keystore.jks", "clientStore");
    }

    private static SSLContext newServerContext() throws IOException, GeneralSecurityException {
        return newSslContext("server_keystore.jks", "serverStore");
    }

    private static SSLContext newSslContext(String name, String pass) throws IOException, GeneralSecurityException {
        KeyStore keystore = loadKeystore(name, pass);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keystore, pass.toCharArray());

        SSLContext context = SSLContext.getInstance(ENABLED_PROTOCOL);
        context.init(kmf.getKeyManagers(), new TrustManager[] {newTrustManager()}, null);
        return context;
    }

    private static X509TrustManager newTrustManager() throws IOException, GeneralSecurityException {
        KeyStore truststore = loadKeystore("truststore.jks", "caStore");
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(truststore);
        return Arrays.stream(tmf.getTrustManagers())
                .filter(trustManager -> trustManager instanceof X509TrustManager)
                .map(trustManager -> (X509TrustManager) trustManager)
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    private static KeyStore loadKeystore(String name, String password) throws IOException, GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream stream = InstrumentedSslContextTest.class
                .getClassLoader()
                .getResource(name)
                .openStream()) {
            keyStore.load(stream, password.toCharArray());
        }
        return keyStore;
    }

    private static MetricName findName(TaggedMetricRegistry metrics, MetricName baseName) {
        return metrics.getMetrics().keySet().stream()
                .filter(name -> Objects.equals(name.safeName(), baseName.safeName())
                        && name.safeTags()
                                .entrySet()
                                .containsAll(baseName.safeTags().entrySet()))
                .collect(MoreCollectors.onlyElement());
    }
}
