package de.dentrassi.hono.simulator.runner;

import static de.dentrassi.hono.demo.common.Environment.getRequired;

import com.openshift.restclient.ClientBuilder;
import com.openshift.restclient.IClient;

public final class OpenShift {

    private OpenShift() {
    }

    public static IClient createIoTClient() {
        return createClient("IOT");
    }

    public static IClient createSimulationClient() {
        return createClient("SIM");
    }

    private static IClient createClient(final String prefix) {
        return new ClientBuilder(
                getRequired(prefix + "_URL"))
                        .withUserName(getRequired(prefix + "_USER"))
                        .withPassword(getRequired(prefix + "_PASSWORD"))
                        .build();
    }

}
