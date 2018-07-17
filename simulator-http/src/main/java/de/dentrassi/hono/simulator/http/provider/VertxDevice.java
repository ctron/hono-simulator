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

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.Statistics;
import de.dentrassi.hono.simulator.http.ThrowingFunction;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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

    private static final Vertx vertx;

    static {
        vertx = Vertx.factory.vertx();
    }

    private final Payload payload;
    private final String telemetryUrl;
    private final String eventUrl;

    private final WebClient client;

    public VertxDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final OkHttpClient client, final Register register, final Payload payload,
            final Statistics telemetryStatistics, final Statistics eventStatistics) {
        super(user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);

        this.payload = payload;

        final WebClientOptions options = new WebClientOptions()
                .setKeepAlive(false);

        this.client = WebClient.create(vertx, options);

        this.telemetryUrl = createUrl("telemetry").toString();
        this.eventUrl = createUrl("event").toString();
    }

    protected CompletableFuture<?> process(final Statistics statistics, final String url) throws IOException {

        final CompletableFuture<?> result = new CompletableFuture<>();

        final HttpRequest<Buffer> request;

        if (this.method.equals("POST")) {
            request = this.client.post(url);
        } else {
            request = this.client.put(url);
        }

        if (!NOAUTH) {
            request.putHeader("Authorization", this.auth);
        }

        request
                .putHeader("Content-Type", this.payload.getContentType())
                .sendBuffer(Buffer.factory.buffer(this.payload.getBytes()), ar -> {

                    final HttpResponse<Buffer> response = ar.result();

                    if (ar.succeeded()) {
                        handleSuccess(statistics);
                    } else {
                        handleFailure(response.statusCode(), statistics);
                    }

                    result.complete(null);
                });

        return result;
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickTelemetryProvider() {
        return s -> process(s, this.telemetryUrl);
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickEventProvider() {
        return s -> process(s, this.eventUrl);
    }

}
