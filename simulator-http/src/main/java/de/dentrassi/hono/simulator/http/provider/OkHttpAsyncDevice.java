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
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Statistics;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class OkHttpAsyncDevice extends OkHttpDevice {

    public static class Provider extends DefaultProvider {

        public Provider() {
            super("OKHTTP_ASYNC", OkHttpAsyncDevice::new);
        }

    }

    private static final Logger logger = LoggerFactory.getLogger(OkHttpAsyncDevice.class);

    public OkHttpAsyncDevice(final String user, final String deviceId, final String tenant, final String password,
            final OkHttpClient client, final Register register, final Statistics telemetryStatistics,
            final Statistics eventStatistics) {
        super(user, deviceId, tenant, password, client, register, telemetryStatistics, eventStatistics);
    }

    @Override
    protected void doPublish(final Supplier<Call> callSupplier, final Statistics statistics) throws Exception {
        statistics.backlog();
        callSupplier.get().enqueue(new Callback() {

            @Override
            public void onResponse(final Call call, final Response response) throws IOException {
                statistics.backlogSent();
                if (response.isSuccessful()) {
                    handleSuccess(statistics);
                } else {
                    logger.trace("Result code: {}", response.code());
                    handleFailure(response.code(), statistics);
                }
                response.close();
            }

            @Override
            public void onFailure(final Call call, final IOException e) {
                statistics.backlogSent();
                handleException(e, statistics);
                logger.debug("Failed to tick", e);
            }
        });
    }
}
