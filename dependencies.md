This document describes installing the Grid container spike to a Kubernetes cluster.


# Minimum dependencies

Before starting you will need to obtain these dependencies.
The specifices of how todo this have been deliberately left up to to the you.

If you are evaluating the Grid them it's probably a useful exercise to build out and understand these dependencies.


## An OAuth provider for sign in

The PANDA signin system will require a 3rd party OAuth provider;
typically this will be a Google Cloud Platform project.

TODO instructions


## AWS S3 image buckets

AWS S3 buckets are the Grid's primary input and outputs.

### Image bucket

Original source images.
This is a private bucket.

### Thumbs bucket

Render thumbnails of the original images.
This is a private bucket used by the Grid UI.

### Crops bucket

Crops exported from the Grid and published here.
This bucket should be a public docroot.


#### CORS

The image and thumbs buckets are accessed from the GRID UO and need to have CORS enabled.
TODO check crops


## AWS Message queues

Grid components share events on an SNS queue.

Each message consumer has a separate SQS queue.
These SQS queues are subscribed to the Grid SNS queue.

### Thrall queue

Update events intended for thrall.

### Metadata queue

Update events intended for meta-data editor.


## PANDA Auth configuration bucket


## API Key bucket

Needed for cropper (TODO try to mitigate this requirement)

ie.
cropper-123abc
```
Cropper
Internal
```

## AWS EC2 Access key

With permission to access the S3 buckets and queues mentioned above.


## A host name

Pick a fully qualified hostname which your Grid deployment will run on.
If this is a local deployment you may need to edit your hosts file to make it resolve.

ie.
```
grid.eelpieconsulting.co.uk
```


## A valid SSL certificate for that hostname

The PANDA auth system mandates secure cookies hence the requirement for SSL even on local deployments.
Self signed certificates might work but your mileage may vary.


## Configuration

Review the supplied configuration files and localise them with the details of your AWS dependencies from above.

Load your configuration into your cluster as config maps

```
kubectl create configmap imgops --from-file=imgops/nginx.conf
```



## Install the Grid components

Apply the following to deploy the Grid services into your cluster. The deployment will pick up configuration from the cluster configmaps you installed above.

By doing this you are downloading and running unofficial 3rd party container images.

Confirm the services are running.



### Ingress

Ensure your cluster has a functioning Ingress controller.
This isn't straight forward but nginx works for a local deployment.


Install your SSL certificate as a cluster secret

Localise and apply the supplied ingress resource.

```
```


Confirm that the ingress is working by hitting the cluster with a browser

```
https://grid.eelpieconsulting.co.uk
```


## Optional dependencies

## Cloudfront

## Permissions bucket

