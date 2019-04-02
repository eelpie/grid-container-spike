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

The Grid uses the [Guardian's Panda authentication system](https://github.com/guardian/pan-domain-authentication).
Panda requires a 3rd party OAuth provider; typically this will be a Google Cloud Platform project.

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


## Snags

Things which changed from master in order of appearance.

Remove common-lib application.conf
Want services to be independent.

Split URL building out from Config; they’re probably separate concerns.
https://github.com/eelpie/grid-container-spike/commit/b03c33269ac94f965951d4b71d82df2d19784f47
https://github.com/eelpie/grid-container-spike/commit/e60f127b9e3efd73b4d9f3bb489f76bf6363c6c6

Move from AWS profile to access keys
As they are easier to inject as config, where as a profile implies specific file system elements been to be in the image.
https://github.com/eelpie/grid-container-spike/commit/1d68ef97b69bf124f6aa243982a5822470abf99b
https://github.com/eelpie/grid-container-spike/commit/5b7f087b932772cf9297f217dda97a62d5d14100

Pull the contains of /etc/gu/*.properties into Play config
So that the properties files don’t need to be in our images; makes the build easier.
https://github.com/eelpie/grid-container-spike/commit/42e957dae42e2afeac3e20e2ed193cbb92e35b3b
https://github.com/eelpie/grid-container-spike/commit/2faed92d643ed3948c4e5ce138ce906bbf6b0f0c

Log to console if you’re not already.
https://github.com/eelpie/grid-container-spike/commit/c0bb99452f29b3fc60cde0a2027c03f7e0dc3802
Also consider stripping logback config out of common-config so that each deploy is in control of it’s own logging.
https://github.com/eelpie/grid-container-spike/commit/8e1b4577dd296254d2d344d1ce4d9e466aff77d8

Build Docker images rather than RiffRaff AMIs
https://github.com/eelpie/grid-container-spike/commit/e092b320b472f9ac89431532eb4edd2d00e3d6a8

Feature toggle Cloud Formation thumbnails
Because it’s hard to setup. If clloudfront config is omitted, then it should just proceed with direct bucket access.

https://github.com/eelpie/grid-container-spike/commit/cbc3b4e309a899c4a05063b8d7300da3507fca13
https://github.com/eelpie/grid-container-spike/commit/9ba2e6d9c645d7d452c30d57a4a814ae4d43da1e
https://github.com/eelpie/grid-container-spike/commit/1852eac093b8fa2d2f4a3dd704196b94c240c3f5

There’s a concept of image buckets which provide image urls (over and above s3 client behaviour) which needs to be codified:
https://github.com/eelpie/grid-container-spike/blob/e16660e32af062ef3176e32be749a4101e16e01c/media-api/app/lib/imagebuckets/ImageBucket.scala
https://github.com/eelpie/grid-container-spike/commit/e16660e32af062ef3176e32be749a4101e16e01c

Feature toggle quotas
Quota config means move buckets and a JSON config format to understand.
https://github.com/eelpie/grid-container-spike/commit/5738f89e7c554e93335c044637ffcd8aed80b35b

Feature toggle out permissions
Looks like DI’ing a permissions handler would help;
https://github.com/eelpie/grid-container-spike/commit/e780b3e7cbffc25e90e6a54a672f4c6009b23afe

Bypassed it by DI’ing a Grant all permissions implementation.
https://github.com/eelpie/grid-container-spike/commit/1c506d27ef9a5f41de053e83b62a541b56e6289d

Pass in colour profiles as config (as the Facebook one might not be open source).
https://github.com/eelpie/grid-container-spike/commit/0c84c86a0751f69747823fa1dcedbe5bd0fcb081

Moved messages to SQS with the new message format.
Pulled and interface for MessageSender so that something like a RabbittMQ implementation can be demoed
https://github.com/eelpie/grid-container-spike/commit/bee52233c77077d8959dea67c177a682b17e41c4
https://github.com/eelpie/grid-container-spike/commit/4189800ecdbd6c03a049f4176ff235efa3f005ab

Featured toggled message queue publishers]
https://github.com/eelpie/grid-container-spike/commit/67111c1de03a0312e1156ede36f3fc41d2606743

Croppers callback to the Media API is problematic if you want to make an internal container to container call:
https://github.com/eelpie/grid-container-spike/commit/d4428dc21333de90fa89d2ef2d989d64d89db3fb
The Cropper media api auth is also a problem as it makes you implement the api key store; pass through the user’s cookie?

Feature toggle CloudWatch metrics.

Kahaua should make signed calls to the crops bucket so that it doesn’t beed special public config (none of the other buckets have this).
