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

import static org.asynchttpclient.Dsl.asyncHttpClient;
import static org.asynchttpclient.Dsl.post;
import static org.asynchttpclient.Dsl.put;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.RequestBuilder;

import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.Statistics;
import de.dentrassi.hono.simulator.http.ThrowingFunction;
import okhttp3.OkHttpClient;

public class AHCDevice extends Device {

    public static class Provider extends DefaultProvider {

        public Provider() {
            super("AHC", AHCDevice::new);
        }

    }

    private static final AsyncHttpClient client;

    static {
        client = asyncHttpClient();
    }

    private final Payload payload;

    private RequestBuilder telemetryRequest;
    private RequestBuilder eventRequest;

    public AHCDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final OkHttpClient client, final Register register, final Payload payload,
            final Statistics telemetryStatistics, final Statistics eventStatistics) {
        super(user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);

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

    protected CompletableFuture<?> process(final Statistics statistics, final RequestBuilder request)
            throws IOException {

        return client
                .executeRequest(request.build())
                .toCompletableFuture()
                .whenComplete((response, ex) -> {

                    if (ex != null) {
                        handleException(ex, statistics);
                    } else {
                        handleResponse(response.getStatusCode(), statistics);
                    }
                });
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickTelemetryProvider() {
        return s -> process(s, this.telemetryRequest);
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickEventProvider() {
        return s -> process(s, this.telemetryRequest);
    }

}
