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
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.Statistics;
import io.glutamate.lang.ThrowingConsumer;
import okhttp3.OkHttpClient;

public class HCDevice extends Device {

    public static class Provider extends DefaultProvider {

        public Provider() {
            super("HC", HCDevice::new);
        }

    }

    private final byte[] payload;
    private final URI telemetryUri;
    private final URI eventUri;

    public HCDevice(final String user, final String deviceId, final String tenant, final String password,
            final OkHttpClient client, final Register register, final Statistics telemetryStatistics,
            final Statistics eventStatistics) {
        super(user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);

        this.payload = "{foo:42}".getBytes(StandardCharsets.UTF_8);

        this.telemetryUri = createUrl("telemetry").uri();
        this.eventUri = createUrl("event").uri();
    }

    @Override
    protected ThrowingConsumer<Statistics> tickTelemetryProvider() {
        return s -> process(s, makeRequest(this.telemetryUri));
    }

    @Override
    protected ThrowingConsumer<Statistics> tickEventProvider() {
        return s -> process(s, makeRequest(this.eventUri));
    }

    private HttpEntityEnclosingRequestBase makeRequest(final URI uri) {
        if ("POST".equals(this.method)) {
            return new HttpPost(uri);
        } else {
            return new HttpPut(uri);
        }
    }

    protected void process(final Statistics statistics, final HttpEntityEnclosingRequestBase request)
            throws IOException {

        request.setHeader("Content-Type", JSON.toString());

        if (!NOAUTH) {
            request.setHeader("Authorization", this.auth);
        }

        request.setEntity(new ByteArrayEntity(this.payload));

        try (
                final CloseableHttpClient httpclient = HttpClients.createDefault();
                final CloseableHttpResponse response = httpclient.execute(request);) {

            final HttpEntity entity = response.getEntity();

            final int code = response.getStatusLine().getStatusCode();
            handleResponse(code, statistics);

            if (entity != null) {
                entity.getContent().close();
            }

        }
    }

}
