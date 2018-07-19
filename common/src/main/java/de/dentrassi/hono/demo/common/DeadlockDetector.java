/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.demo.common;

import java.io.Closeable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeadlockDetector implements Closeable {

    private final ScheduledExecutorService deadlockExecutor;

    public DeadlockDetector() {

        this.deadlockExecutor = Executors.newSingleThreadScheduledExecutor();
        this.deadlockExecutor.scheduleAtFixedRate(DeadlockDetector::detectDeadlock, 1, 1, TimeUnit.SECONDS);

    }

    private static void detectDeadlock() {
        final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        final long[] threadIds = threadBean.findDeadlockedThreads();

        if (threadIds != null) {
            System.out.format("Threads in deadlock: %s%n", threadIds.length);
        }
    }

    @Override
    public void close() {
        this.deadlockExecutor.shutdown();
        try {
            this.deadlockExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
        }
    }
}
