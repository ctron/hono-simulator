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

import static de.dentrassi.hono.demo.common.CompletableFutures.runAsync;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import de.dentrassi.hono.demo.common.EventWriter;
import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.Response;
import de.dentrassi.hono.simulator.http.Statistics;
import de.dentrassi.hono.simulator.http.ThrowingFunction;
import okhttp3.OkHttpClient;

public class HCDevice extends Device {

    public static class Provider extends DefaultProvider {

        public Provider() {
            super("HC", HCDevice::new);
        }

    }

    private final Payload payload;
    private final URI telemetryUri;
    private final URI eventUri;
    private final Executor executor;

    public HCDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final OkHttpClient client, final Register register, final Payload payload,
            final Statistics telemetryStatistics, final Statistics eventStatistics, final EventWriter eventWriter) {
        super(user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);

        this.executor = executor;
        this.payload = payload;

        this.telemetryUri = createUrl("telemetry").uri();
        this.eventUri = createUrl("event").uri();
    }

    private HttpEntityEnclosingRequestBase makeRequest(final URI uri) {
        if ("POST".equals(this.method)) {
            return new HttpPost(uri);
        } else {
            return new HttpPut(uri);
        }
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickTelemetryProvider() {
        return s -> runAsync(() -> process(s, makeRequest(this.telemetryUri)), this.executor);
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickEventProvider() {
        return s -> runAsync(() -> process(s, makeRequest(this.eventUri)), this.executor);
    }

    protected void process(final Statistics statistics, final HttpEntityEnclosingRequestBase request)
            throws IOException {

        request.setHeader("Content-Type", this.payload.getContentType());

        if (!NOAUTH) {
            request.setHeader("Authorization", this.auth);
        }

        request.setEntity(new ByteArrayEntity(this.payload.getBytes()));

        try (
                final CloseableHttpClient httpclient = HttpClients.createDefault();
                final CloseableHttpResponse response = httpclient.execute(request);) {

            final HttpEntity entity = response.getEntity();

            final int code = response.getStatusLine().getStatusCode();
            handleResponse(new Response() {

                @Override
                public int code() {
                    return code;
                }

                @Override
                public String bodyAsString(final Charset charset) {
                    try (InputStream in = response.getEntity().getContent()) {
                        return IOUtils.toString(in, charset);
                    } catch (final IOException e) {
                        return null;
                    }
                }
            }, statistics);

            if (entity != null) {
                entity.getContent().close();
            }

        }
    }

}
