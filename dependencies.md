# Minimum dependencies


## Image buckets

S3 buckets are the Grid's primary input and outputs.

### Image bucket

Original source images.
This is a private bucket.


### Thumbs bucket

Render thumbnails of the original images.
This is a private bucket used by the Grid UI.


### Crops bucket

Crops exported from the Grid and published here.
This bucket should be a public docroot.


### CORS

The image and thumbs buckets are acccessed from the GRID UO and need to have CORS enabled.
TODO check crops


## Message queues

Grid events are published onto an SNS queue.

Each message consumer has a seperate SQS queue. 
These SQS queues are subscribed to the Grid SNS queue.

### Thrall queue

Update events intended for thrall.

### Metadata queue

Update events intended for meta-data editor.



## API Key bucket

Needed for cropper

ie.
cropper-123abc
```
Cropper
Internal
```




```
kubectl create configmap imgops --from-file=imgops/nginx.conf
```




## Optional dependencies

## Cloudfront

## Permissions bucket

