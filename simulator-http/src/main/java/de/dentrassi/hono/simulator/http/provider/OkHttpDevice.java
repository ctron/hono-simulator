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

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.Statistics;
import io.glutamate.lang.ThrowingConsumer;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;

public abstract class OkHttpDevice extends Device {

    private final OkHttpClient client;

    private final RequestBody body;
    private final Request telemetryRequest;
    private final Request eventRequest;

    public OkHttpDevice(final String user, final String deviceId, final String tenant, final String password,
            final OkHttpClient client, final Register register, final Statistics telemetryStatistics,
            final Statistics eventStatistics) {
        super(user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);

        this.client = client;

        this.body = RequestBody.create(JSON, "{foo: 42}");

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

        if (HONO_HTTP_URL == null) {
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
    protected ThrowingConsumer<Statistics> tickTelemetryProvider() {
        return (statistics) -> doPublish(this::createTelemetryCall, statistics);
    }

    @Override
    protected ThrowingConsumer<Statistics> tickEventProvider() {
        return (statistics) -> doPublish(this::createEventCall, statistics);
    }

    protected abstract void doPublish(final Supplier<Call> call, final Statistics statistics) throws Exception;

}
