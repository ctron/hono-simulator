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
package de.dentrassi.hono.simulator.consumer;

import static io.glutamate.lang.Environment.is;
import static io.vertx.core.CompositeFuture.join;
import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.config.ClientConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.DeadlockDetector;
import de.dentrassi.hono.demo.common.Tenant;
import io.glutamate.lang.Environment;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.netty.handler.ssl.OpenSsl;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.micrometer.MetricsDomain;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private final Vertx vertx;
    private final HonoClient honoClient;
    private final CountDownLatch latch;
    private final String tenant;

    private final Consumer telemetryConsumer;

    private final Consumer eventConsumer;

    private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;

    public static void main(final String[] args) throws Exception {

        try (final DeadlockDetector detector = new DeadlockDetector()) {

            final Application app = new Application(
                    Tenant.TENANT,
                    Environment.get("MESSAGING_SERVICE_HOST").orElse("localhost"), // HONO_DISPATCH_ROUTER_EXT_SERVICE_HOST
                    Environment.getAs("MESSAGING_SERVICE_PORT_AMQP", 5671, Integer::parseInt), // HONO_DISPATCH_ROUTER_EXT_SERVICE_PORT
                    getenv("HONO_USER"),
                    getenv("HONO_PASSWORD"),
                    ofNullable(getenv("HONO_TRUSTED_CERTS")));

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
            final Optional<String> trustedCerts) {

        System.out.format("Hono Consumer - Server: %s:%s%n", host, port);

        this.tenant = tenant;
        System.out.format("Hono tenant: %s%n", this.tenant);

        final VertxOptions options = new VertxOptions();

        options.setPreferNativeTransport(true);
        options.setMetricsOptions(
                new MicrometerMetricsOptions()
                        .setEnabled(true)
                        .setDisabledMetricsCategories(EnumSet.allOf(MetricsDomain.class))
                        .setPrometheusOptions(
                                new VertxPrometheusOptions()
                                        .setEmbeddedServerOptions(
                                                new HttpServerOptions()
                                                        .setPort(8081))
                                        .setEnabled(true)
                                        .setStartEmbeddedServer(true)));

        this.vertx = Vertx.vertx(options);

        final MeterRegistry registry = BackendRegistries.getDefaultNow();

        System.out.println("Vertx Native: " + this.vertx.isNativeTransportEnabled());

        System.out.format("OpenSSL - available: %s -> %s%n", OpenSsl.isAvailable(), OpenSsl.versionString());
        System.out.println("Key Manager: " + OpenSsl.supportsKeyManagerFactory());
        System.out.println("Host name validation: " + OpenSsl.supportsHostnameValidation());

        final ClientConfigProperties config = new ClientConfigProperties();
        config.setHost(host);
        config.setPort(port);
        config.setUsername(user);
        config.setPassword(password);

        if (System.getenv("DISABLE_TLS") == null) {
            config.setTlsEnabled(true);
            config.setHostnameVerificationRequired(false);
        }

        trustedCerts.ifPresent(config::setTrustStorePath);

        Environment.getAs("HONO_INITIAL_CREDITS", Integer::parseInt).ifPresent(config::setInitialCredits);

        this.honoClient = HonoClient.newClient(this.vertx, config);

        this.latch = new CountDownLatch(1);

        final Tags commonTags = Tags
                .of("tenant", this.tenant);

        this.telemetryConsumer = new Consumer(
                registry.counter("messages.received", commonTags.and("type", "telemetry")));
        this.eventConsumer = new Consumer(
                registry.counter("messages.received", commonTags.and("type", "event")));

    }

    private void close() {
        this.honoClient.shutdown(done -> {
        });
        this.vertx.close();
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

    private Future<MessageConsumer> createTelemetryConsumer(final HonoClient connectedClient) {

        return connectedClient.createTelemetryConsumer(this.tenant,
                this.telemetryConsumer::handleMessage, closeHandler -> {

                    logger.info("close handler of telemetry consumer is called");

                    System.err.println("Lost TelemetryConsumer link, restarting …");
                    System.exit(-1);

                    this.vertx.setTimer(DEFAULT_CONNECT_TIMEOUT_MILLIS, reconnect -> {
                        logger.info("attempting to re-open the TelemetryConsumer link ...");
                        createTelemetryConsumer(connectedClient);
                    });
                });
    }

    private Future<MessageConsumer> createEventConsumer(final HonoClient connectedClient) {

        return connectedClient.createEventConsumer(this.tenant,
                this.eventConsumer::handleMessage, closeHandler -> {

                    logger.info("close handler of event consumer is called");

                    System.err.println("Lost EventConsumer link, restarting …");
                    System.exit(-1);

                    this.vertx.setTimer(DEFAULT_CONNECT_TIMEOUT_MILLIS, reconnect -> {
                        logger.info("attempting to re-open the EventConsumer link ...");
                        createEventConsumer(connectedClient);
                    });
                });
    }

    private void onDisconnect(final ProtonConnection con) {

        // reconnect still seems to have issues
        System.err.println("Connection to Hono lost, restarting …");
        System.exit(-1);

        this.vertx.setTimer(DEFAULT_CONNECT_TIMEOUT_MILLIS, reconnect -> {

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
                    return join(
                            createTelemetryConsumer(connectedClient),
                            createEventConsumer(connectedClient));
                });

    }

}