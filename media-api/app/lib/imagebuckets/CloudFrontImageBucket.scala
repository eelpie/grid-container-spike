package lib.imagebuckets

import java.io.File
import java.net.URI

import com.amazonaws.services.cloudfront.CloudFrontUrlSigner
import com.amazonaws.services.cloudfront.util.SignerUtils.Protocol
import com.gu.mediaservice.lib.aws.S3
import com.gu.mediaservice.model.Image
import lib.MediaApiConfig
import org.joda.time.DateTime

import scala.util.Try

trait CloudFrontDistributable {
  def cloudFrontDomain: String
  def privateKeyLocation: String
  def keyPairId: String

  val validForMinutes = 30

  private lazy val privateKeyFile: File = new File(privateKeyLocation)

  def signedCloudFrontUrl(s3ObjectPath: String): Option[String] = Try {
    val expiresAt = DateTime.now.plusMinutes(validForMinutes).toDate
    CloudFrontUrlSigner.getSignedURLWithCannedPolicy(
      Protocol.https, cloudFrontDomain, privateKeyFile, s3ObjectPath, keyPairId, expiresAt)
  }.toOption
}

class CloudFrontImageBucket(config: MediaApiConfig, val cloudFrontDomain: String, val privateKeyLocation: String, val keyPairId: String)
  extends S3(config) with CloudFrontDistributable with ImageBucket {

  override def signedUrlFor(uri: URI, image: Image): String = {
    val path = uri.getPath.drop(1)
    signedCloudFrontUrl(path).get   // TODO Naked get
  }

}