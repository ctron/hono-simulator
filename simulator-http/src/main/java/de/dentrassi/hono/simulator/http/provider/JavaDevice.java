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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Device;
import de.dentrassi.hono.simulator.http.Statistics;
import io.glutamate.lang.ThrowingConsumer;
import okhttp3.OkHttpClient;

public class JavaDevice extends Device {

    public static class Provider extends DefaultProvider {

        public Provider() {
            super("JAVA", JavaDevice::new);
        }

    }

    private final byte[] payload;
    private final URL telemetryUrl;
    private final URL eventUrl;

    public JavaDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final OkHttpClient client, final Register register,
            final Statistics telemetryStatistics, final Statistics eventStatistics) {
        super(executor, user, deviceId, tenant, password, register, telemetryStatistics, eventStatistics);
        this.payload = "{foo:42}".getBytes(StandardCharsets.UTF_8);

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
            con.setRequestProperty("Content-Type", JSON.toString());

            if (!NOAUTH) {
                con.setRequestProperty("Authorization", this.auth);
            }

            con.connect();

            try (final OutputStream out = con.getOutputStream()) {
                out.write(this.payload);
            }

            final int code = con.getResponseCode();
            handleResponse(code, statistics);

        } finally {
            con.disconnect();
        }
    }

    @Override
    protected ThrowingConsumer<Statistics> tickTelemetryProvider() {
        return s -> process(s, this.telemetryUrl);
    }

    @Override
    protected ThrowingConsumer<Statistics> tickEventProvider() {
        return s -> process(s, this.eventUrl);
    }

}
