This document describes installing the Grid container spike to a Kubernetes cluster.

# Changes from master

This branch differs from master in these key ways:

## Removing configuration from the deployables

The Guardian deployment assumes that various configuration files will be available at specific file system locations (application config files ans EC2 profiles)
This is incompatible with the idea that the configuration should be provided by the container orchestrator.

The Grid applications are Play framework applications. We'd like move all the configuration back to standard Play
application.conf files with one file per application. We're happy to tollerate some duplication in the configuration
as this helps to document the dependencies which each specific deployable really needs.

Specifically, we've removed the dependencies on /etc/gu and the Guardian native format .properties files. EC2 access is migrated
from profiles to access keys.


## Disabling optional features with differcult dependencies

To enable the simplest possible unboxing some advanced features with differcult dependencies have been feature toggled off.
These include Cloud Front thumbnails, permissions, costs and Cloud Watch metrics. These features can be reenabled by
provisioning their dependencies and then configuring them.


## Publishing public Docker images

With all of user specific configuration removed from the Docker images it's safe to publish them to public repositories on docker hub.
This makes then available for use by users who do not wish to build their own deployables, speeding up the unboxing experience.


## Remapping servives onto a single hostname

Making it easier to obtain the required SSL certificate.


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
crops?


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


Review the supplied configuration files and localise them with the details of your AWS dependencies from above.

Load your configuration into your cluster as config maps

```
cd kubernetes/conf

kubectl create configmap leases --from-file=leases/application.conf 
kubectl create configmap imgops --from-file=imgops/nginx.conf
```



## Install the Grid components

Apply the following to deploy the Grid services into your cluster.
By doing this you are downloading and running unofficial 3rd party container images.
The deployment will pick up configuration from the cluster config maps you installed above.

```
kubectl apply -f https://raw.githubusercontent.com/eelpie/grid-container-spike/master/grid.yaml
```

Confirm the services are running.


### Ingress

Ensure that your cluster has a functioning Ingress controller.
This isn't straight forward but nginx works for a local deployment.

Install your SSL certificate as a cluster secret

Localise and apply the supplied ingress resource.

```
```

Confirm that the ingress is working by hitting the cluster with a browser

```
https://grid.eelpieconsulting.co.uk
```

