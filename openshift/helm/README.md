Getting Helm installed in OpenShift:

https://blog.openshift.com/getting-started-helm-openshift/

    oc new-project tiller
    export TILLER_NAMESPACE=tiller
    oc process -f https://github.com/openshift/origin/raw/master/examples/helm/tiller-template.yaml -p TILLER_NAMESPACE="${TILLER_NAMESPACE}" -p HELM_VERSION=v2.12.3 | oc create -f -

And then:

    oc new-project sim-1
    oc policy add-role-to-user edit "system:serviceaccount:${TILLER_NAMESPACE}:tiller"
    oc policy add-role-to-user admin "system:serviceaccount:${TILLER_NAMESPACE}:tiller"
    oc policy add-role-to-user cluster-admin "system:serviceaccount:${TILLER_NAMESPACE}:tiller"
    helm install  --name sim ./iot-simulator
