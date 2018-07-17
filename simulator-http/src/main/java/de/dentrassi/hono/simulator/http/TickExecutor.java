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
package de.dentrassi.hono.simulator.http;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TickExecutor {

    private final ScheduledExecutorService executor;

    public TickExecutor(final int numberOfThreads) {
        this.executor = Executors.newScheduledThreadPool(numberOfThreads);
    }

    public void shutdown() {
        this.executor.shutdown();
    }

    public void scheduleAtFixedRate(final Runnable runnable, final long initialPeriod, final long period) {

        this.executor.schedule(() -> {

            doRun(runnable, period);

        }, initialPeriod, TimeUnit.MILLISECONDS);

    }

    private void doRun(final Runnable runnable, final long period) {

        final Instant now = Instant.now();

        try {
            runnable.run();
        } finally {

            final Runnable doRunner = () -> {
                doRun(runnable, period);
            };

            final long diff = period - Duration.between(now, Instant.now()).toMillis();
            if (diff < 0) {
                this.executor.execute(doRunner);
            } else {
                this.executor.schedule(doRunner, diff, TimeUnit.MILLISECONDS);
            }
        }

    }

}
