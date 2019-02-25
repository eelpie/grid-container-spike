package lib

import com.amazonaws.services.ec2.{AmazonEC2, AmazonEC2ClientBuilder}
import com.gu.mediaservice.lib.config.CommonConfig
import play.api.Configuration

import scala.util.Try

class MediaApiConfig(override val configuration: Configuration) extends CommonConfig {

  final override lazy val appName = "media-api"

  lazy val usageStoreBucket: Option[String] = configuration.getOptional[String]("s3.usagemail.bucket")
  lazy val quotaStoreBucket: Option[String] = configuration.getOptional[String]("s3.config.bucket")
  lazy val quotaStoreFile: Option[String] = configuration.getOptional[String]("quota.store.key")
  lazy val quotaUpdateEnabled: Option[Boolean] = configuration.getOptional[Boolean]("quota.update.enabled")

  private lazy val ec2Client: AmazonEC2 = withAWSCredentials(AmazonEC2ClientBuilder.standard()).build()

  lazy val imagesAlias: String = configuration.get[String]("es.index.aliases.read")

  lazy val elasticsearchCluster: Option[String] = configuration.getOptional[String]("es.cluster")

  lazy val elasticsearchHost: Option[String] = configuration.getOptional[String]("es.host")
  lazy val elasticsearchPort: Option[Int] = configuration.getOptional[Int]("es.port")

  lazy val elasticsearch6Host: Option[String] = configuration.getOptional[String]("es6.host")
  lazy val elasticsearch6Port: Option[Int] = configuration.getOptional[Int]("es6.port")
  lazy val elasticsearch6Cluster: Option[String] = configuration.getOptional[String]("es6.cluster")
  lazy val elasticsearch6Shards: Option[Int] = configuration.getOptional[Int](("es6.shards"))
  lazy val elasticsearch6Replicas: Option[Int] = configuration.getOptional[Int](("es6.replicas"))

  lazy val imageBucket: String = configuration.get[String]("s3.image.bucket")
  lazy val thumbBucket: String = configuration.get[String]("s3.thumb.bucket")

  case class CloudFrontConfiguration(cloudFrontPrivateKeyLocation: String, cloudFrontKeyPairId: String)

  lazy val cloudFrontConfiguration = for {
    cloudFrontPrivateKeyLocation <- configuration.getOptional[String]("cloudfront.private.key.location")
    cloudFrontKeyPairId <-  configuration.getOptional[String]("cloudfront.keypair.id")
  } yield {
    CloudFrontConfiguration(cloudFrontPrivateKeyLocation, cloudFrontKeyPairId)
  }

  lazy val topicArn: String = configuration.get[String]("sns.topic.arn")

  lazy val requiredMetadata = List("credit", "description", "usageRights")

  lazy val persistenceIdentifier: String = configuration.get[String]("persistence.identifier")
  lazy val queriableIdentifiers = Seq(persistenceIdentifier)

  lazy val persistedRootCollections: List[String] = configuration.getOptional[String]("persistence.collections") match {
    case Some(collections) => collections.split(',').toList
    case None => List("GNM Archive")
  }

  def convertToInt(s: String): Option[Int] = Try { s.toInt }.toOption

}
