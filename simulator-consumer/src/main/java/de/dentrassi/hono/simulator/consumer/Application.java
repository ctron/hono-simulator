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

import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.AppRuntime;
import de.dentrassi.hono.demo.common.DeadlockDetector;
import de.dentrassi.hono.demo.common.Tenant;
import de.dentrassi.hono.demo.common.Type;
import io.glutamate.lang.Environment;
import io.netty.handler.ssl.OpenSsl;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(final String[] args) throws Exception {

        try (final DeadlockDetector detector = new DeadlockDetector()) {

            final Application app = new Application(
                    Tenant.TENANT,
                    Environment.get("MESSAGING_SERVICE_HOST").orElse("localhost"), // HONO_DISPATCH_ROUTER_EXT_SERVICE_HOST
                    Environment.getAs("MESSAGING_SERVICE_PORT_AMQP", 5671, Integer::parseInt), // HONO_DISPATCH_ROUTER_EXT_SERVICE_PORT
                    getenv("HONO_USER"),
                    getenv("HONO_PASSWORD"),
                    ofNullable(getenv("HONO_TRUSTED_CERTS"))
                    );

            try {
                app.process();
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

    private final HonoContext hono;

    private final CountDownLatch latch = new CountDownLatch(1);

    public Application(final String tenant, final String host, final int port, final String user, final String password,
            final Optional<String> trustedCerts) {

        final var type = Type.fromEnv();

        System.out.format("Hono Consumer - Server: %s:%s%n", host, port);

        System.out.format("    Tenant: %s%n", tenant);
        System.out.format("    Consuming: %s%n", type);

        final var runtime = new AppRuntime();

        System.out.println("Vertx Native: " + runtime.getVertx().isNativeTransportEnabled());

        System.out.format("OpenSSL - available: %s -> %s%n", OpenSsl.isAvailable(), OpenSsl.versionString());
        System.out.println("Key Manager: " + OpenSsl.supportsKeyManagerFactory());
        System.out.println("Host name validation: " + OpenSsl.supportsHostnameValidation());

        this.hono = new HonoContext(tenant, host, port, user, password, trustedCerts, type, runtime);

    }

    private void process() throws Exception {
        this.hono.connect()
                .setHandler(startup -> {
                    if (startup.failed()) {
                        logger.error("Error occurred during initialization of receiver", startup.cause());
                        this.latch.countDown();
                    }
                });

        // if everything went according to plan, the next step will block forever

        this.latch.await();
    }

    private void close() {
        this.hono.close();
    }


}
