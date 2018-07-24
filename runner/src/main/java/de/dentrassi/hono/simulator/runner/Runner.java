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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.simulator.runner.state.Context;
import de.dentrassi.hono.simulator.runner.state.State;

public class Runner implements Context, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(Runner.class);

    private State state;
    private final ScheduledExecutorService scheduler;
    private final CompletableFuture<?> complete;

    private final ScheduledFuture<?> job;

    public Runner(final State state, final Duration period) {
        this.state = state;
        this.complete = new CompletableFuture<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        this.scheduler.execute(state::start);
        this.job = this.scheduler.scheduleWithFixedDelay(this::check, 0, period.toMillis(), TimeUnit.MILLISECONDS);
    }

    protected void check() {
        if (this.state == null) {
            close();
            return;
        }

        final State current = this.state;

        try {
            current.check(this);
        } catch (final Exception e) {
            logger.info("Failed to check", e);
            this.complete.completeExceptionally(new RuntimeException("Failed to process check", e));
            return;
        }

        if (this.state != current) {
            // state changed ... check right now
            this.scheduler.execute(this::check);
        }
    }

    public CompletableFuture<?> complete() {
        return this.complete;
    }

    @Override
    public void advance(final State state) {

        logger.info("Advance to: {}", state);

        this.state = state;

        if (state == null) {
            this.complete.complete(null);
        } else {
            state.start();
        }
    }

    @Override
    public void close() {
        if (this.job != null) {
            this.job.cancel(false);
        }
        this.complete.completeExceptionally(new RuntimeException("Runner closed"));
        this.scheduler.shutdown();
    }

}
