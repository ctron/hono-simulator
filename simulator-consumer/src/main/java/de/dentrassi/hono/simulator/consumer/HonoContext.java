/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/

package de.dentrassi.hono.simulator.consumer;

import static io.glutamate.lang.Environment.is;

import java.util.Optional;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.config.ClientConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.AppRuntime;
import de.dentrassi.hono.demo.common.Tls;
import de.dentrassi.hono.demo.common.Type;
import io.glutamate.lang.Environment;
import io.micrometer.core.instrument.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

public class HonoContext implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(HonoContext.class);

    private final AppRuntime runtime;

    private final HonoClient honoClient;
    private final String tenant;

    private final Consumer consumer;

    private final Type type;

    private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;

    public HonoContext(final String tenant, final String host, final int port, final String user, final String password,
            final Optional<String> trustedCerts, final Type type, final AppRuntime runtime) {

        this.tenant = tenant;
        this.type = type;
        this.runtime = runtime;

        final ClientConfigProperties config = new ClientConfigProperties();
        config.setHost(host);
        config.setPort(port);
        config.setUsername(user);
        config.setPassword(password);

        if (!Tls.disabled()) {
            config.setTlsEnabled(true);
            config.setHostnameVerificationRequired(false);
            System.out.println("TLS enabled");
        }

        config.setReconnectAttempts(-1);

        trustedCerts.ifPresent(config::setTrustStorePath);

        Environment.getAs("HONO_INITIAL_CREDITS", Integer::parseInt).ifPresent(config::setInitialCredits);

        this.honoClient = HonoClient.newClient(this.runtime.getVertx(), config);

        runtime.register("client-connected", future -> {
            isConnected(this.honoClient, future);
        });

        final Tags commonTags = Tags
                .of("tenant", this.tenant)
                .and(type.asTag());

        this.consumer = new Consumer(
                this.runtime.getRegistry().counter("messages.received", commonTags),
                this.runtime.getRegistry().counter("payload.received", commonTags));
    }

    private ConsumerProvider getConsumerProvider(final HonoClient connectedClient) {
        final ConsumerProvider provider;
        switch (this.type) {
        case EVENT:
            provider = connectedClient::createEventConsumer;
            break;
        default:
            provider = connectedClient::createTelemetryConsumer;
            break;
        }
        return provider;
    }

    private static void isConnected(final HonoClient honoClient, final Handler<AsyncResult<Status>> handler) {
        honoClient.isConnected()
                .map(v -> Status.OK())
                .setHandler(handler);
    }

    @Override
    public void close() {
        this.honoClient.shutdown(done -> {
        });
        this.runtime.close();
    }

    public Future<?> connect() {

        return this.honoClient.connect(
                getOptions(),
                this::onDisconnect)

                .compose(connectedClient -> {
                    logger.info("connected to Hono");
                    return createConsumer(connectedClient);
                });

    }

    static private ProtonClientOptions getOptions() {
        final ProtonClientOptions options = new ProtonClientOptions();

        is("WITH_OPENSSL", () -> {
            System.out.println("Using OpenSSL for proton");
            options.setSslEngineOptions(new OpenSSLEngineOptions());
        });

        return options;
    }

    private void onDisconnect(final ProtonConnection con) {

        // reconnect still seems to have issues
        System.err.println("Connection to Hono lost, restarting …");
        System.exit(-1);

        this.runtime.getVertx().setTimer(DEFAULT_CONNECT_TIMEOUT_MILLIS, reconnect -> {

            logger.info("attempting to re-connect to Hono ...");
            connect();

        });

    }

    private Future<MessageConsumer> createConsumer(final HonoClient connectedClient) {

        final var provider = getConsumerProvider(connectedClient);

        return provider.createConsumer(this.tenant,
                this.consumer::handleMessage, closeHandler -> {

                    logger.info("close handler of consumer is called");

                    System.err.println("Lost Consumer link, restarting …");
                    System.exit(-1);

                    this.runtime.getVertx().setTimer(DEFAULT_CONNECT_TIMEOUT_MILLIS, reconnect -> {
                        logger.info("attempting to re-open the Consumer link ...");
                        createConsumer(connectedClient);
                    });
                });
    }

}
