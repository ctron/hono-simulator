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

package de.dentrassi.hono.simulator.http;

import java.util.function.Supplier;

import de.dentrassi.hono.demo.common.Register;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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

        if ("POST".equals(METHOD)) {
            this.telemetryRequest = createPostRequest("/telemetry");
            this.eventRequest = createPostRequest("/event");
        } else {
            this.telemetryRequest = createPutRequest("telemetry");
            this.eventRequest = createPutRequest("event");
        }
    }

    private Request createPostRequest(final String type) {

        if (HONO_HTTP_URL == null) {
            return null;
        }

        final Request.Builder builder = new Request.Builder()
                .url(HONO_HTTP_URL.resolve(type))
                .post(this.body);

        if (!NOAUTH) {
            builder.header("Authorization", this.auth);
        }

        return builder.build();
    }

    private Request createPutRequest(final String type) {
        final Request.Builder builder = new Request.Builder()
                .url(
                        HONO_HTTP_URL.newBuilder()
                                .addPathSegment(type)
                                .addPathSegment(this.tenant)
                                .addPathSegment(this.deviceId)
                                .build())
                .put(this.body);

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
    public void tickTelemetry() {
        tick(this.telemetryStatistics, () -> doPublish(this::createTelemetryCall, this.telemetryStatistics));
    }

    @Override
    public void tickEvent() {
        tick(this.eventStatistics, () -> doPublish(this::createEventCall, this.eventStatistics));
    }

    protected abstract void doPublish(final Supplier<Call> call, final Statistics statistics) throws Exception;

}
