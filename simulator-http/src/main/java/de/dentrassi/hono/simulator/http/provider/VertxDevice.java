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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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

    public static class Provider extends DefaultProvider {

        public Provider() {
            super("VERTX", VertxDevice::new);
        }

    }

    private static Vertx vertx;

    private static WebClient client;

    private static void initialize() {

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

        final WebClientOptions clientOptions = new WebClientOptions();

        consumeAs("VERTX_KEEP_ALIVE", Boolean::parseBoolean, clientOptions::setKeepAlive);
        consumeAs("VERTX_MAX_POOL_SIZE", Integer::parseInt, clientOptions::setMaxPoolSize);
        consumeAs("VERTX_POOLED_BUFFERS", Boolean::parseBoolean, clientOptions::setUsePooledBuffers);

        client = WebClient.create(vertx, clientOptions);
    }

    private final Payload payload;

    private final Buffer payloadBuffer;

    private HttpRequest<Buffer> telemetryClient;
    private HttpRequest<Buffer> eventClient;

    public VertxDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final OkHttpClient client, final Register register, final Payload payload,
            final Statistics telemetryStatistics, final Statistics eventStatistics) {
        super(user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);

        initialize();

        this.payload = payload;
        this.payloadBuffer = Buffer.factory.buffer(this.payload.getBytes());

        final String telemetryUrl = createUrl("telemetry").toString();
        final String eventUrl = createUrl("event").toString();

        if (this.method.equals("POST")) {
            this.telemetryClient = VertxDevice.client.postAbs(telemetryUrl);
            this.eventClient = VertxDevice.client.postAbs(eventUrl);
        } else {
            this.telemetryClient = VertxDevice.client.putAbs(telemetryUrl);
            this.eventClient = VertxDevice.client.putAbs(eventUrl);
        }

        if (!NOAUTH) {
            this.telemetryClient.putHeader("Authorization", this.auth);
            this.eventClient.putHeader("Authorization", this.auth);
        }

        this.telemetryClient.putHeader("Content-Type", this.payload.getContentType());
        this.eventClient.putHeader("Content-Type", this.payload.getContentType());

    }

    protected CompletableFuture<?> process(final Statistics statistics, final HttpRequest<Buffer> request)
            throws IOException {

        final CompletableFuture<?> result = new CompletableFuture<>();

        request
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
        return s -> process(s, this.telemetryClient);
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickEventProvider() {
        return s -> process(s, this.eventClient);
    }

}
