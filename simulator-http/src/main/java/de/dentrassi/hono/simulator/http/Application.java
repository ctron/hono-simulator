/*******************************************************************************
 * Copyright (c) 2017, 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.simulator.http;

import static de.dentrassi.hono.demo.common.Select.oneOf;
import static io.glutamate.lang.Environment.consumeAs;
import static io.glutamate.lang.Environment.getAs;
import de.dentrassi.hono.demo.common.AppRuntime;
import de.dentrassi.hono.demo.common.ProducerConfig;
import de.dentrassi.hono.demo.common.DeadlockDetector;
import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.demo.common.Tenant;
import de.dentrassi.hono.demo.common.Tls;
import io.glutamate.lang.Environment;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.netty.handler.ssl.OpenSsl;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import okhttp3.Credentials;

public class Application {

    private static final ProducerConfig config = ProducerConfig.fromEnv();

    private static final String METHOD = Environment.get("HTTP_METHOD").orElse("PUT");

    private static final boolean NOAUTH = Environment.getAs("HTTP_NOAUTH", false, Boolean::parseBoolean);

    private static final String PASSWORD = Environment.get("DEVICE_PASSWORD").orElse("hono-secret");

    public static final boolean AUTO_REGISTER = Environment.getAs("AUTO_REGISTER", true, Boolean::parseBoolean);

    public static void main(final String[] args) throws Exception {

        try (
                DeadlockDetector detector = new DeadlockDetector();
                AppRuntime runtime = new AppRuntime();) {
            runSimulator(runtime);
        } catch (final Exception e) {
            System.err.println("Failed to initialize application");
            e.printStackTrace();
            System.exit(1);
        }

    }

    private static void runSimulator(final AppRuntime runtime) throws InterruptedException {

        final int numberOfDevices = getAs("NUM_DEVICES", 10, Integer::parseInt);
        final int numberOfThreads = getAs("NUM_THREADS", 10, Integer::parseInt);

        System.out.format("#devices: %s, #threads: %s%n", numberOfDevices, numberOfThreads);
        System.out.format("Auto Register: %s%n", AUTO_REGISTER);
        System.out.format("TLS insecure: %s%n", Tls.insecure());

        System.out.println("Vertx Native: " + runtime.getVertx().isNativeTransportEnabled());

        System.out.format("OpenSSL - available: %s -> %s%n", OpenSsl.isAvailable(), OpenSsl.versionString());
        System.out.println("Key Manager: " + OpenSsl.supportsKeyManagerFactory());
        System.out.println("Host name validation: " + OpenSsl.supportsHostnameValidation());

        // create new client

        final String deviceIdPrefix = System.getenv("HOSTNAME");

        final Register register = new Register(Tenant.TENANT);
        final MeterRegistry registry = runtime.getRegistry();

        final Tags commonTags = Tags.of(
                Tag.of("protocol", "http"),
                Tag.of("tenant", Tenant.TENANT),
                config.getType().asTag());
        final Statistics stats = new Statistics(registry, commonTags);

        final var webClient = createWebClient(runtime.getVertx());

        for (int i = 0; i < numberOfDevices; i++) {

            final String username = String.format("user-%s-%s", deviceIdPrefix, i);
            final String deviceId = String.format("%s-%s", deviceIdPrefix, i);

            final var request = createRequest(webClient, config, Payload.payload(), Tenant.TENANT, deviceId,
                    username, PASSWORD);

            final Device device = new Device(runtime.getVertx(), () -> request, config, username, deviceId,
                    Tenant.TENANT, PASSWORD, register, Payload.payload(), stats);
            device.start();

        }

        Thread.sleep(Long.MAX_VALUE);

    }

    private static WebClient createWebClient(final Vertx vertx) {
        final WebClientOptions clientOptions = new WebClientOptions();

        consumeAs("VERTX_KEEP_ALIVE", Boolean::parseBoolean, clientOptions::setKeepAlive);
        consumeAs("VERTX_MAX_POOL_SIZE", Integer::parseInt, clientOptions::setMaxPoolSize);
        consumeAs("VERTX_POOLED_BUFFERS", Boolean::parseBoolean, clientOptions::setUsePooledBuffers);

        clientOptions.setConnectTimeout(getAs("VERTX_CONNECT_TIMEOUT", 5_000, Integer::parseInt));
        clientOptions.setIdleTimeout(getAs("VERTX_IDLE_TIMEOUT", 5_000, Integer::parseInt));

        if (Tls.insecure()) {
            clientOptions.setVerifyHost(false);
            clientOptions.setTrustAll(true);
        }

        if (vertx.isNativeTransportEnabled()
                && OpenSsl.isAvailable()
                && OpenSsl.supportsKeyManagerFactory()
                && OpenSsl.supportsHostnameValidation()) {
            clientOptions.setOpenSslEngineOptions(new OpenSSLEngineOptions());
        }

        return WebClient.create(vertx, clientOptions);
    }

    private static HttpRequest<Buffer> createRequest(final WebClient client, final ProducerConfig config,
            final Payload payload, final String tenant, final String deviceId, final String user,
            final String password) {

        final var auth = Credentials.basic(user + "@" + tenant, password);
        final var url = buildUrl(config, tenant, deviceId);

        final HttpRequest<Buffer> request;

        switch (METHOD) {
        case "POST":
            request = client.postAbs(url);
            break;
        default:
            request = client.putAbs(url);
            break;
        }

        if (!NOAUTH) {
            request.putHeader("Authorization", auth);
        }

        request.putHeader("Content-Type", payload.getContentType());

        return request;
    }

    private static String buildUrl(final ProducerConfig config, final String tenant, final String deviceId) {

        final String url = oneOf(System.getenv("HONO_HTTP_URL"));
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("'HONO_HTTP_URL' is missing or blank");
        }

        final var builder = new StringBuilder(url);

        if (!url.endsWith("/")) {
            builder.append("/");
        }

        switch (config.getType()) {
        case EVENT:
            builder.append("event");
            break;
        case TELEMETRY:
            builder.append("telemetry");
            break;
        }

        switch (METHOD) {
        case "POST":
            break;
        default:
            builder.append("/").append(tenant).append("/").append(deviceId);
            break;
        }

        return builder.toString();
    }
}
