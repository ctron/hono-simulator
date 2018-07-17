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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import io.glutamate.lang.ThrowingRunnable;

public final class CompletableFutures {

    private CompletableFutures() {
    }

    public static <X extends Exception> CompletableFuture<?> runAsync(final ThrowingRunnable<X> runnable,
            final Executor executor) {

        final CompletableFuture<?> result = new CompletableFuture<>();

        executor.execute(() -> {
            try {
                runnable.run();
                result.complete(null);
            } catch (final Exception e) {
                result.completeExceptionally(e);
            }
        });

        return result;
    }

}
