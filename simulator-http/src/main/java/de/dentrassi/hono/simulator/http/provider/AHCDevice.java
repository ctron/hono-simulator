/*******************************************************************************
 * Copyright (c) 2018, 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.simulator.http.provider;

import static io.glutamate.lang.Environment.*;
import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.post;
import static org.asynchttpclient.Dsl.put;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig.Builder;
import org.asynchttpclient.RequestBuilder;

import de.dentrassi.hono.demo.common.EventWriter;
import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.Response;
import de.dentrassi.hono.simulator.http.Statistics;
import de.dentrassi.hono.simulator.http.ThrowingSupplier;
import io.netty.buffer.PooledByteBufAllocator;
import okhttp3.OkHttpClient;

public class AHCDevice extends Device {

    public static class Provider extends DefaultProvider {

        public Provider() {
            super("AHC", AHCDevice::new);
        }

    }

    private static AsyncHttpClient client;

    private synchronized static void initialize() {

        if (client != null) {
            return;
        }

        final Builder config = new Builder();

        is("AHC_POOLED_ALLOCATOR", () -> config.setAllocator(PooledByteBufAllocator.DEFAULT));
        getAs("AHC_IO_THREAD_COUNT", Integer::parseInt).ifPresent(config::setIoThreadsCount);
        getAs("AHC_MAX_CONNECTIONS", Integer::parseInt).ifPresent(config::setMaxConnections);
        getAs("AHC_MAX_CONNECTIONS_PER_HOST", Integer::parseInt).ifPresent(config::setMaxConnectionsPerHost);
        getAs("AHC_KEEP_ALIVE", Boolean::parseBoolean).ifPresent(config::setKeepAlive);
        getAs("AHC_USE_NATIVE_TRANSPORT", Boolean::parseBoolean).ifPresent(config::setUseNativeTransport);

        client = asyncHttpClient(config);
    }

    private final Payload payload;

    private RequestBuilder telemetryRequest;
    private RequestBuilder eventRequest;

    public AHCDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final OkHttpClient client, final Register register, final Payload payload,
            final Statistics statistics, final EventWriter eventWriter) {
        super(user, deviceId, tenant, password, register, statistics);

        initialize();

        this.payload = payload;

        final String telemetryUrl = createUrl("telemetry").toString();
        final String eventUrl = createUrl("event").toString();

        if (this.method.equals("POST")) {
            this.telemetryRequest = post(telemetryUrl);
            this.eventRequest = post(eventUrl);
        } else {
            this.telemetryRequest = put(telemetryUrl);
            this.eventRequest = put(eventUrl);
        }

        if (!NOAUTH) {
            this.telemetryRequest.setHeader("Authorization", this.auth);
            this.eventRequest.setHeader("Authorization", this.auth);
        }

        this.telemetryRequest.setHeader("Content-Type", this.payload.getContentType());
        this.telemetryRequest.setBody(payload.getBytes());

        this.eventRequest.setHeader("Content-Type", this.payload.getContentType());
        this.eventRequest.setBody(payload.getBytes());
    }

    protected CompletableFuture<?> process(final RequestBuilder request)
            throws IOException {

        return client
                .executeRequest(request.build())
                .toCompletableFuture()
                .whenComplete((response, ex) -> {

                    if (ex != null) {
                        handleException(ex);
                    } else {
                        handleResponse(new Response() {

                            @Override
                            public int code() {
                                return response.getStatusCode();
                            }

                            @Override
                            public String bodyAsString(final Charset charset) {
                                return response.getResponseBody(charset);
                            }
                        });
                    }
                });
    }

    @Override
    protected ThrowingSupplier<CompletableFuture<?>, Exception> tickTelemetryProvider() {
        return () -> process(this.telemetryRequest);
    }

    @Override
    protected ThrowingSupplier<CompletableFuture<?>, Exception> tickEventProvider() {
        return () -> process(this.telemetryRequest);
    }

}
