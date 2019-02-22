package lib.imagebuckets

import java.net.URI

import com.gu.mediaservice.model.Image

trait ImageBucket {
  def signedUrlFor(uri: URI, image: Image): String
}