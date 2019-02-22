package lib.imagebuckets

import java.net.URI

import com.gu.mediaservice.lib.aws.S3
import com.gu.mediaservice.model.Image
import lib.MediaApiConfig

class S3ImageBucket(config: MediaApiConfig, bucket: String) extends S3(config) with ImageBucket {
  override def signedUrlFor(uri: URI, image: Image): String = {
    this.signUrl(bucket, uri: URI, image: Image)
  }
}