
# Deployment on OpenShift

Extract the certificates from the EnMasse instance:

    mkdir certs
    oc extract -n enmasse secret/external-certs-messaging --to=certs

Create a new project for the simulator:

    oc new-project simulator --display-name "Hono Simulator"

And then create a config map with the certificates:

    oc create  configmap simulator-config --from-file=server-cert.pem=certs/ca.crt

Then deploy the simulator template:

    oc process -p IOT_CLUSTER_DNS_BASENAME=<my-hono-hostname> -f template.yml | oc create -f -

**Note:** By default the simulators (HTTP and MQTT) will have zero (0) replicas.
You will need to scale them up in order to generate some load.