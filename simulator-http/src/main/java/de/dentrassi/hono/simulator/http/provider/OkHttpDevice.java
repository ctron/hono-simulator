/*******************************************************************************
 * Copyright (c) 2017, 2018 Red Hat Inc and others.
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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.Statistics;
import de.dentrassi.hono.simulator.http.ThrowingFunction;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;

public abstract class OkHttpDevice extends Device {

    private final OkHttpClient client;

    private final RequestBody body;
    private final Request telemetryRequest;
    private final Request eventRequest;

    public OkHttpDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final OkHttpClient client, final Register register, final Payload payload,
            final Statistics telemetryStatistics, final Statistics eventStatistics) {
        super(user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);

        this.client = client;

        this.body = RequestBody.create(MediaType.parse(payload.getContentType()), payload.getBytes());

        this.telemetryRequest = createRequest(createUrl("telemetry"), method());
        this.eventRequest = createRequest(createUrl("event"), method());

    }

    private BiConsumer<Builder, RequestBody> method() {
        if ("POST".equals(this.method)) {
            return Request.Builder::post;
        } else {
            return Request.Builder::put;
        }
    }

    private Request createRequest(final HttpUrl url, final BiConsumer<Request.Builder, RequestBody> method) {

        if (!this.enabled) {
            return null;
        }

        final Request.Builder builder = new Request.Builder()
                .url(url);

        method.accept(builder, this.body);

        if (!NOAUTH) {
            builder.header("Authorization", this.auth);
        }

        return builder.build();
    }

    private Call createTelemetryCall() {
        return this.client.newCall(this.telemetryRequest);
    }

    private Call createEventCall() {
        return this.client.newCall(this.eventRequest);
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickTelemetryProvider() {
        return (statistics) -> doPublish(this::createTelemetryCall, statistics);
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickEventProvider() {
        return (statistics) -> doPublish(this::createEventCall, statistics);
    }

    protected abstract CompletableFuture<?> doPublish(final Supplier<Call> call, final Statistics statistics)
            throws Exception;

    protected de.dentrassi.hono.simulator.http.Response toResponse(final Response response) {
        return new de.dentrassi.hono.simulator.http.Response() {

            @Override
            public int code() {
                return response.code();
            }

            @Override
            public String bodyAsString() {
                try {
                    return response.body().string();
                } catch (final IOException e) {
                    return null;
                }
            }

            @Override
            public String bodyAsString(final Charset charset) {
                try {
                    return charset.decode(ByteBuffer.wrap(response.body().bytes())).toString();
                } catch (final IOException e) {
                    return null;
                }
            }
        };
    }
}
