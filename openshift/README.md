
# Deployment on OpenShift

## Extracting certificates

Create a new directory for storing the certificates:

    mkdir certs

### EnMasse before 0.23

    oc extract -n enmasse secret/external-certs-messaging --to=certs

### EnMasse 0.23+

    oc -n <project-name> get addressspace <address-space-name> -o jsonpath={.status.endpointStatuses[?(@.name==\'messaging\')].cert} | base64 -d > certs/ca.crt

For the default Hono OpenShift S2I deployment this would be:

    oc -n hono get addressspace default -o jsonpath={.status.endpointStatuses[?(@.name==\'messaging\')].cert} | base64 -d > certs/ca.crt

## Deploying the simulator

Create a new project for the simulator:

    oc new-project hono-simulator --display-name "Hono Simulator"

And then create a config map with the certificates:

    oc create configmap simulator-config --from-file=server-cert.pem=certs/ca.crt

Then deploy the simulator template:

    oc process -p IOT_CLUSTER_DNS_BASENAME=<my-hono-hostname> -f template.yml | oc create -f -

If you are using Minishift you can use the Minishift IP address with e.g. "nip.io":

    oc process -p IOT_CLUSTER_DNS_BASENAME=$(minishift ip).nip.io -f template.yml | oc create -f -

**Note:** By default the simulators (HTTP and MQTT) will have zero (0) replicas.
You will need to scale them up in order to generate some load.

## Tweaking Hono default settings

The default limitation on the Hono device registry may not be sufficient for
registering a larger number of devices. You can raise the limit be executing
the following command:

    oc env -n hono dc/hono-service-device-registry HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT=10000
