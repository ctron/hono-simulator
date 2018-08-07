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
package de.dentrassi.hono.simulator.runner.state;

import static io.glutamate.util.Collections.map;
import static java.lang.String.format;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.restclient.IClient;

import de.dentrassi.hono.demo.common.EventWriter;

public class ScaleUp extends AbstractNextState {

    private static final Logger logger = LoggerFactory.getLogger(ScaleUp.class);

    private final EventWriter eventWriter;
    private final IClient client;

    private final String namespace;
    private final String resourceKind;
    private final String resource;

    private final int limit;

    public ScaleUp(final EventWriter eventWriter, final IClient client, final String namespace,
            final String resourceKind, final String resource, final int limit) {
        this.eventWriter = eventWriter;
        this.client = client;

        this.namespace = namespace;
        this.resourceKind = resourceKind;
        this.resource = resource;

        this.limit = limit;
    }

    @Override
    public void check(final Context context) {

        logger.info("Scaling up - {}/{}", this.resourceKind, this.resource);

        final DeploymentConfig dc = this.client.getResourceFactory().stub(this.resourceKind, this.resource,
                this.namespace);
        dc.refresh();
        final int r = dc.getReplicas() + 1;

        if (r > this.limit) {
            System.out.format("Limit reached - %s/%s%n", this.resourceKind, this.resource);
            context.advance(null);
            return;
        }

        logger.info("Scaling to - {}/{} = {}", this.resourceKind, this.resource, r);

        this.eventWriter.writeEvent("runner", "Scale Up",
                format("%s/%s -> %s", this.resourceKind, this.resource, r), map(map -> {
                    map.put("kind", this.resourceKind);
                    map.put("resource", this.resource);
                    map.put(this.resourceKind, this.resource);
                }));
        dc.setReplicas(r);
        this.client.update(dc);

        advance(context);
    }

}
