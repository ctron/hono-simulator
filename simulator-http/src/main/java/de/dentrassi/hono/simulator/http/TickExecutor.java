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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class TickExecutor {

    private final ScheduledExecutorService executor;

    public TickExecutor() {
        this.executor = Executors.newScheduledThreadPool(1);
    }

    public void shutdown() {
        this.executor.shutdown();
    }

    public void scheduleAtFixedRate(final Supplier<CompletableFuture<?>> runnable, final long initialPeriod,
            final long period) {

        this.executor.schedule(() -> {

            doRun(runnable, period);

        }, initialPeriod, TimeUnit.MILLISECONDS);

    }

    private void doRun(final Supplier<CompletableFuture<?>> runnable, final long period) {

        final Instant now = Instant.now();

        final CompletableFuture<?> future;

        try {
            future = runnable.get();
        } catch (final Exception e) {
            finishRun(runnable, period, now);
            return;
        }

        future.handle((r, ex) -> {
            finishRun(runnable, period, now);
            return null;
        });
    }

    private void finishRun(final Supplier<CompletableFuture<?>> runnable, final long period, final Instant now) {
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
