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
package de.dentrassi.hono.simulator.consumer;

import static io.glutamate.lang.Environment.is;
import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.config.ClientConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.AppRuntime;
import de.dentrassi.hono.demo.common.DeadlockDetector;
import de.dentrassi.hono.demo.common.Tenant;
import de.dentrassi.hono.demo.common.Tls;
import de.dentrassi.hono.demo.common.Type;
import io.glutamate.lang.Environment;
import io.micrometer.core.instrument.Tags;
import io.netty.handler.ssl.OpenSsl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.ext.healthchecks.Status;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private final AppRuntime runtime;

    private final HonoClient honoClient;
    private final CountDownLatch latch;
    private final String tenant;

    private final Consumer consumer;

    private final Type type;

    private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;

    public static void main(final String[] args) throws Exception {

        final var type = Type.fromEnv();

        try (final DeadlockDetector detector = new DeadlockDetector()) {

            final Application app = new Application(
                    Tenant.TENANT,
                    Environment.get("MESSAGING_SERVICE_HOST").orElse("localhost"), // HONO_DISPATCH_ROUTER_EXT_SERVICE_HOST
                    Environment.getAs("MESSAGING_SERVICE_PORT_AMQP", 5671, Integer::parseInt), // HONO_DISPATCH_ROUTER_EXT_SERVICE_PORT
                    getenv("HONO_USER"),
                    getenv("HONO_PASSWORD"),
                    ofNullable(getenv("HONO_TRUSTED_CERTS")),
                    type
                    );

            try {
                app.consumeMessages();
                System.out.println("Exiting application ...");
            } finally {
                app.close();
            }

        }
        System.out.println("Bye, bye!");

        Thread.sleep(1_000);

        for (final Thread t : Thread.getAllStackTraces().keySet()) {
            System.out.println(t.getName());
        }

        System.exit(-1);
    }

    public Application(final String tenant, final String host, final int port, final String user, final String password,
            final Optional<String> trustedCerts, final Type type) {

        System.out.format("Hono Consumer - Server: %s:%s%n", host, port);

        this.tenant = tenant;
        this.type = type;

        System.out.format("    Tenant: %s%n", this.tenant);
        System.out.format("    Consuming: %s%n", this.type);

        this.runtime = new AppRuntime();

        System.out.println("Vertx Native: " + this.runtime.getVertx().isNativeTransportEnabled());

        System.out.format("OpenSSL - available: %s -> %s%n", OpenSsl.isAvailable(), OpenSsl.versionString());
        System.out.println("Key Manager: " + OpenSsl.supportsKeyManagerFactory());
        System.out.println("Host name validation: " + OpenSsl.supportsHostnameValidation());

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
            isConnected(honoClient, future);
        });

        this.latch = new CountDownLatch(1);

        final Tags commonTags = Tags
                .of("tenant", this.tenant)
                .and(type.asTag());

        this.consumer = new Consumer(this.runtime.getRegistry().counter("messages.received", commonTags));

    }

    private static void isConnected(final HonoClient honoClient, final Handler<AsyncResult<Status>> handler) {
        honoClient.isConnected()
                .map(v -> Status.OK())
                .setHandler(handler);
    }

    private void close() {
        this.runtime.close();
        this.honoClient.shutdown(done -> {
        });
    }

    private ProtonClientOptions getOptions() {
        final ProtonClientOptions options = new ProtonClientOptions();

        is("WITH_OPENSSL", () -> {
            options.setSslEngineOptions(new OpenSSLEngineOptions());
        });

        return options;
    }

    private void consumeMessages() throws Exception {
        connect()
                .setHandler(startup -> {
                    if (startup.failed()) {
                        logger.error("Error occurred during initialization of receiver", startup.cause());
                        this.latch.countDown();
                    }
                });

        // if everything went according to plan, the next step will block forever

        this.latch.await();
    }

    @FunctionalInterface
    interface ConsumerProvider {

        Future<MessageConsumer> createConsumer(String tenantId, java.util.function.Consumer<Message> consumer,
                Handler<Void> closeHandler);
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

    private void onDisconnect(final ProtonConnection con) {

        // reconnect still seems to have issues
        System.err.println("Connection to Hono lost, restarting …");
        System.exit(-1);

        this.runtime.getVertx().setTimer(DEFAULT_CONNECT_TIMEOUT_MILLIS, reconnect -> {

            logger.info("attempting to re-connect to Hono ...");
            connect();

        });

    }

    private Future<?> connect() {

        return this.honoClient.connect(
                getOptions(),
                this::onDisconnect)

                .compose(connectedClient -> {
                    logger.info("connected to Hono");
                    return createConsumer(connectedClient);
                });

    }

}
