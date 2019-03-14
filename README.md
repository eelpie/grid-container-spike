This document describes installing the Grid container spike to a Kubernetes cluster.

# Changes from master

This branch differs from master in these key ways:

## Removing configuration from the deployables

The Guardian deployment assumes that various configuration files will be available at specific file system locations (application config files ans EC2 profiles)
This is incompatible with the idea that the configuration should be provided by the container orchestrator.

The Grid applications are Play framework applications. We'd like move all the configuration back to standard Play
application.conf files with one file per application. We're happy to tolerate some duplication in the configuration
as this helps to document the dependencies which each specific deployable really needs.

Specifically, we've removed the dependencies on /etc/gu and the Guardian native format .properties files. EC2 access is migrated from profiles to access keys.


## Disabling optional features with difficult dependencies

To enable the simplest possible unboxing some advanced features with differcult dependencies have been feature toggled off.
These include Cloud Front thumbnails, permissions, costs and Cloud Watch metrics. These features can be renabled by provisioning their dependencies and then configuring them.


## Publishing public Docker images

With all of user specific configuration removed from the Docker images it's safe to publish them to public repositories on docker hub.
This makes then available for use by users who do not wish to build their own deployables, speeding up the unboxing experience.


## Remapping services onto a single hostname

The Guardian deployment maps each service to a unique hostname; this ingress used here maps services to sub paths on the same hostname as the UI.
This makes it easier to obtain the required SSL certificate.


# Setup



## Provide minimum dependencies

Before starting you will need to obtain these dependencies.
The specifices of how todo this have been deliberately left up to to the you.

If you are evaluating the Grid them it's probably a useful exercise to build out and understand these dependencies.


### An OAuth provider for sign in

The PANDA signin system will require a 3rd party OAuth provider;
typically this will be a Google Cloud Platform project.

TODO instructions


### AWS S3 image buckets

AWS S3 buckets are the Grid's primary input and outputs.

#### Image bucket

Original source images.
This is a private bucket.

#### Thumbs bucket

Render thumbnails of the original images.
This is a private bucket used by the Grid UI.

#### Crops bucket

Crops exported from the Grid and published here.

This bucket should be a public docroot.


CORS

The image, thumbs and crops buckets are accessed from the GRID UI and need to have CORS enabled.



### AWS Message queues

Grid components share events on an SNS queue.

Each message consumer has a separate SQS queue.
These SQS queues are subscribed to the Grid SNS queue.

#### Thrall queue

Update events intended for thrall.

#### Metadata queue

Update events intended for meta-data editor.


### Dynamo Tables

collections, image-collections, leases and metadata


### PANDA Auth configuration bucket


### API Key bucket

Needed for cropper (TODO try to mitigate this requirement)

ie.
cropper-123abc
```
Cropper
Internal
```


### AWS EC2 Access key

An AWS EC2 access key with permission to access the S3 buckets and queues mentioned above.


### A host name

Pick a fully qualified, correctly resolving hostname which your Grid deployment will run on.
If this is a local deployment you may need to edit your hosts file to make it resolve.

ie.
```
grid.eelpieconsulting.co.uk
```


### A valid SSL certificate for that hostname

The PANDA auth system mandates secure cookies, hence the requirement for SSL even on local deployments.
Self signed certificates might work but your mileage may vary.


## Configuration

Review the [skeleton configuration files](kubernetes/) and localise them with the details of your AWS dependencies from above.

Load your configuration into your cluster as config maps

```
cd kubernetes

kubectl create configmap auth --from-file=auth/application.conf
kubectl create configmap collections --from-file=collections/application.conf
kubectl create configmap cropper --from-file=cropper/application.conf
kubectl create configmap image-loader --from-file=cropper/application.conf
kubectl create configmap imgops --from-file=imgops/nginx.conf
kubectl create configmap kahuna --from-file=kahuna/application.conf
kubectl create configmap leases --from-file=leases/application.conf
kubectl create configmap media-api --from-file=media-api/application.conf
kubectl create configmap metadata-editor --from-file=metadata-editor/application.conf
kubectl create configmap thrall --from-file=thrall/application.cond
```

Colour profiles are supplied as configuration:

```
kubectl create configmap profiles --from-file=profiles
```


## Install the Grid components

Apply the following to deploy the Grid services into your cluster.
By doing this you are downloading and running unofficial 3rd party container images.
The deployment will pick up configuration from the cluster config maps you installed above.

```
kubectl apply -f https://raw.githubusercontent.com/eelpie/grid-container-spike/master/kubernetes/grid.yaml
```

Confirm the services are registered:

```
kubectl get services
auth               NodePort    10.99.200.175    <none>        9011:32111/TCP   13s
collections        NodePort    10.111.153.73    <none>        9010:32110/TCP   13s
cropper            NodePort    10.100.178.10    <none>        9006:32106/TCP   13s
image-loader       NodePort    10.104.139.51    <none>        9003:32103/TCP   13s
imgops             NodePort    10.98.11.134     <none>        80:32108/TCP     13s
kahuna             NodePort    10.103.44.54     <none>        9005:32105/TCP   13s
leases             NodePort    10.104.113.30    <none>        9012:32112/TCP   13s
media-api          NodePort    10.105.126.161   <none>        9001:32101/TCP   13s
metadata-editor    NodePort    10.97.64.111     <none>        9007:32107/TCP   13s
thrall             NodePort    10.102.15.69     <none>        9002:32102/TCP   13s
```

And that the pods have started:

```
tony@kubernetes:~/git/kubernetes/grid$ kubectl get pods
NAME                                READY   STATUS              RESTARTS   AGE
auth-5f6bccdf65-pxh5m               0/1     ContainerCreating   0          100s
collections-6794d66cb8-c7k6d        0/1     ContainerCreating   0          100s
cropper-58d8f98fc7-g9wmm            0/1     ContainerCreating   0          100s
image-loader-ccc5cb76-p9hp4         0/1     ContainerCreating   0          100s
imgops-579597f775-lkwgw             0/1     ContainerCreating   0          100s
kahuna-f87c9d68d-gsg9l              0/1     ContainerCreating   0          100s
leases-86d6d5bd98-flmlg             0/1     ContainerCreating   0          99s
media-api-bc4b47f8-mkvlj            0/1     ContainerCreating   0          99s
metadata-editor-84bb8b79b-fgvk9     0/1     ContainerCreating   0          99s
thrall-5c47c5b4ff-9xk8q             0/1     ContainerCreating   0          99s
```


### Ingress

Ensure that your cluster has a functioning Ingress controller.
This isn't straight forward but [NGINX Ingress Controller](https://github.com/nginxinc/kubernetes-ingress) works for a local deployment.

Install your SSL certificate as a cluster secret

```
kubectl create secret tls grid-ssl-secret --key privkey.pem --cert fullchain.pem
```

Localise and apply the supplied ingress resource.
This ingress resource has been tested with the NGINX Ingress Controller; milage will almost certainly vary with Cloud ingress controllers.

```
kubectl apply -f ingress-resource.yaml
```

Confirm that the ingress is working by hitting the cluster with a browser

```
https://grid.eelpieconsulting.co.uk
```
