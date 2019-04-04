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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;

import io.vertx.core.Future;
import io.vertx.core.Handler;

@FunctionalInterface
interface ConsumerProvider {

    Future<MessageConsumer> createConsumer(String tenantId, java.util.function.Consumer<Message> consumer,
            Handler<Void> closeHandler);
}