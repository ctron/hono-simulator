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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import de.dentrassi.hono.demo.common.Payload;
import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.Statistics;
import de.dentrassi.hono.simulator.http.ThrowingFunction;
import okhttp3.OkHttpClient;

public class JavaDevice extends Device {

    public static class Provider extends DefaultProvider {

        public Provider() {
            super("JAVA", JavaDevice::new);
        }

    }

    private final Executor executor;

    private final Payload payload;
    private final URL telemetryUrl;
    private final URL eventUrl;

    public JavaDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final OkHttpClient client, final Register register, final Payload payload,
            final Statistics telemetryStatistics, final Statistics eventStatistics) {
        super(user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);
        this.executor = executor;
        this.payload = payload;

        this.telemetryUrl = createUrl("telemetry").url();
        this.eventUrl = createUrl("event").url();
    }

    protected void process(final Statistics statistics, final URL url) throws IOException {

        final HttpURLConnection con = (HttpURLConnection) url.openConnection();
        try {
            con.setDoInput(false);
            con.setDoOutput(true);
            con.setUseCaches(false);

            con.setConnectTimeout(1_000);
            con.setReadTimeout(1_000);
            con.setRequestMethod(this.method);
            con.setRequestProperty("Content-Type", this.payload.getContentType());

            if (!NOAUTH) {
                con.setRequestProperty("Authorization", this.auth);
            }

            con.connect();

            try (final OutputStream out = con.getOutputStream()) {
                this.payload.write(out);
            }

            final int code = con.getResponseCode();
            handleResponse(code, statistics);

        } finally {
            con.disconnect();
        }
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickTelemetryProvider() {
        return s -> runAsync(() -> process(s, this.telemetryUrl), this.executor);
    }

    @Override
    protected ThrowingFunction<Statistics, CompletableFuture<?>, Exception> tickEventProvider() {
        return s -> runAsync(() -> process(s, this.eventUrl), this.executor);
    }

}
