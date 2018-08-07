/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.simulator.runner;

import java.time.Duration;

public class Dumper implements AutoCloseable {

    public static void main(final String[] args) {

        try (Dumper app = new Dumper("telemetry")) {
            app.run();
        }

    }

    private final Metrics metrics;

    public Dumper(final String type) {
        this.metrics = new Metrics("telemetry", Duration.ofMinutes(1));
    }

    public void run() {
        final Duration period = Duration.ofMinutes(3);
        System.out.format("Failure rate:         %8.2f%%%n", this.metrics.getFailureRate(period) * 100.0);
        System.out.format("RTT:               %8d ms%n", this.metrics.getRtt(period));
        System.out.format("Sent messages:     %8d msg/s%n", this.metrics.getSentMessages(period));
        System.out.format("Received messages: %8d msg/s%n", this.metrics.getReceivedMessages(period));
    }

    @Override
    public void close() {
        this.metrics.close();
    }

}
