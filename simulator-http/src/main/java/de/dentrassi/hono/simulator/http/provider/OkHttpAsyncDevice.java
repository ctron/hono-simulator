/*******************************************************************************
 * Copyright (c) 2017, 2019 Red Hat Inc and others.
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.EventWriter;
import de.dentrassi.hono.demo.common.Payload;
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

    public OkHttpAsyncDevice(final Executor executor, final String user, final String deviceId, final String tenant,
            final String password, final OkHttpClient client, final Register register, final Payload payload,
            final Statistics statistics, final EventWriter eventWriter) {
        super(executor, user, deviceId, tenant, password, client, register, payload, statistics);
    }

    @Override
    protected CompletableFuture<?> doPublish(final Supplier<Call> callSupplier) throws Exception {

        final CompletableFuture<?> result = new CompletableFuture<>();

        callSupplier.get().enqueue(new Callback() {

            @Override
            public void onResponse(final Call call, final Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        handleSuccess();
                    } else {
                        logger.trace("Result code: {}", response.code());
                        handleFailure(toResponse(response));
                    }
                    response.close();
                } finally {
                    result.complete(null);
                }
            }

            @Override
            public void onFailure(final Call call, final IOException e) {
                try {
                    handleException(e);
                    logger.debug("Failed to tick", e);
                } finally {
                    result.completeExceptionally(e);
                }
            }
        });

        return result;
    }

}
