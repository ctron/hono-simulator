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

import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.Register;
import de.dentrassi.hono.simulator.http.Statistics;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class OkHttpSyncDevice extends OkHttpDevice {

    public static class Provider extends DefaultProvider {

        public Provider() {
            super("OKHTTP", OkHttpSyncDevice::new);
        }

    }

    private static final Logger logger = LoggerFactory.getLogger(OkHttpSyncDevice.class);

    public OkHttpSyncDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final OkHttpClient client, final Register register,
            final Statistics telemetryStatistics, final Statistics eventStatistics) {
        super(executor, user, deviceId, tenant, password, client, register, telemetryStatistics, eventStatistics);
    }

    @Override
    protected void doPublish(final Supplier<Call> callSupplier, final Statistics statistics) throws Exception {
        try (final Response response = callSupplier.get().execute()) {
            if (response.isSuccessful()) {
                handleSuccess(statistics);
            } else {
                logger.trace("Result code: {}", response.code());
                handleFailure(response.code(), statistics);
            }
        }
    }
}
