
# Deployment on OpenShift

## Extracting certificates

Create a new directory for storing the certificates:

    mkdir certs

Extract the certificates from a pre-0.23 EnMasse instance:

    oc extract -n enmasse secret/external-certs-messaging --to=certs

Extract the certificates from a 0.23+ EnMasse instance:

    oc extract -n enmasse secret/external-certs-messaging-<project-name>-<address-space-name> --to=certs

For the default Hono OpenShift S2I deployment this would be:

    oc extract -n enmasse secret/external-certs-messaging-hono-default --to=certs

## Deploying the simulator

Create a new project for the simulator:

    oc new-project hono-simulator --display-name "Hono Simulator"

And then create a config map with the certificates:

    oc create  configmap simulator-config --from-file=server-cert.pem=certs/ca.crt

Then deploy the simulator template:

    oc process -p IOT_CLUSTER_DNS_BASENAME=<my-hono-hostname> -f template.yml | oc create -f -

**Note:** By default the simulators (HTTP and MQTT) will have zero (0) replicas.
You will need to scale them up in order to generate some load.