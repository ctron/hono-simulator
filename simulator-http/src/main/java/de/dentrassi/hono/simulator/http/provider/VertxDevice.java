/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.simulator.http.provider;

import static de.dentrassi.hono.demo.common.Environment.consumeAs;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.Environment;
import de.dentrassi.hono.demo.common.EventWriter;
import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.Statistics;
import de.dentrassi.hono.simulator.http.ThrowingFunction;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import okhttp3.OkHttpClient;

public class VertxDevice extends Device {

    private static final Logger logger = LoggerFactory.getLogger(VertxDevice.class);

    public static class Provider extends DefaultProvider {

        public Provider() {
            super("VERTX", VertxDevice::new);
        }

    }

    private static Vertx vertx;

    private static final AtomicReference<WebClient> client = new AtomicReference<>();

    private static void initialize(final EventWriter eventWriter) {

        if (vertx != null) {
            return;
        }

        final VertxOptions options = new VertxOptions();

        options.setPreferNativeTransport(true);

        final AddressResolverOptions addressResolverOptions = new AddressResolverOptions();
        consumeAs("VERTX_DNS_MAX_TTL", Integer::parseInt, addressResolverOptions::setCacheMaxTimeToLive);
        options.setAddressResolverOptions(addressResolverOptions);

        vertx = Vertx.factory.vertx(options);

        final boolean usingNative = vertx.isNativeTransportEnabled();
        System.out.println("VERTX: Running with native: " + usingNative);

        createWebClient(eventWriter);

        Environment.consumeAs("VERTX_RECREATE_CLIENT", Long::parseLong, period -> {
            vertx.setPeriodic(period, t -> createWebClient(eventWriter));
        });

    }

    private static void createWebClient(final EventWriter eventWriter) {
        logger.info("Creating new web client");

        eventWriter.writeEvent(Instant.now(), "Web Client", "Creating new vertx web clients");

        final WebClientOptions clientOptions = new WebClientOptions();

        consumeAs("VERTX_KEEP_ALIVE", Boolean::parseBoolean, clientOptions::setKeepAlive);
        consumeAs("VERTX_MAX_POOL_SIZE", Integer::parseInt, clientOptions::setMaxPoolSize);
        consumeAs("VERTX_POOLED_BUFFERS", Boolean::parseBoolean, clientOptions::setUsePooledBuffers);

        final WebClient oldClient = client.getAndSet(WebClient.create(vertx, clientOptions));
        if (oldClient != null) {
            oldClient.close();
        }
    }

    private final Payload payload;

    private final Buffer payloadBuffer;

    private final String telemetryUrl;

    private final String eventUrl;

    public VertxDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final OkHttpClient client, final Register register, final Payload payload,
            final Statistics telemetryStatistics, final Statistics eventStatistics, final EventWriter eventWriter) {
        super(user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);

        initialize(eventWriter);

        this.payload = payload;
        this.payloadBuffer = Buffer.factory.buffer(this.payload.getBytes());

        this.telemetryUrl = createUrl("telemetry").toString();
        this.eventUrl = createUrl("event").toString();
    }

    private HttpRequest<Buffer> createRequest(final String url) {

        final HttpRequest<Buffer> request;

        if (this.method.equals("POST")) {
            request = VertxDevice.client.get().postAbs(url);
        } else {
            request = VertxDevice.client.get().putAbs(url);
        }

        if (!NOAUTH) {
            request.putHeader("Authorization", this.auth);
        }

        request.putHeader("Content-Type", this.payload.getContentType());

        return request;
    }

    private HttpRequest<Buffer> createTelemetryRequest() {
        return createRequest(this.telemetryUrl);
    }

    private HttpRequest<Buffer> createEventRequest() {
        return createRequest(this.eventUrl);
    }

    protected CompletableFuture<?> process(final Statistics statistics, final Supplier<HttpRequest<Buffer>> request)
            throws IOException {

        final CompletableFuture<?> result = new CompletableFuture<>();

        request
                .get()
                .sendBuffer(this.payloadBuffer, ar -> {

                    final HttpResponse<Buffer> response = ar.result();

                    if (ar.succeeded()) {
                        handleResponse(response.statusCode(), statistics);
                        result.complete(null);
                    } else {
                        handleException(ar.cause(), statistics);
                        result.completeExceptionally(ar.cause());
                    }

                });

        return result;
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickTelemetryProvider() {
        return s -> process(s, this::createTelemetryRequest);
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickEventProvider() {
        return s -> process(s, this::createEventRequest);
    }

}
