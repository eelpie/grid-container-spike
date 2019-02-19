package lib

import com.amazonaws.services.ec2.{AmazonEC2, AmazonEC2ClientBuilder}
import com.gu.mediaservice.lib.config.CommonConfig
import play.api.Configuration

import scala.util.Try

class MediaApiConfig(override val configuration: Configuration) extends CommonConfig {

  final override lazy val appName = "media-api"

  lazy val usageStoreBucket: String = properties("s3.usagemail.bucket")

  lazy val quotaStoreBucket: String = properties("s3.config.bucket")
  lazy val quotaStoreFile: String = properties("quota.store.key")

  // quota updates can only be turned off in DEV
  lazy val quotaUpdateEnabled: Boolean = if (isDev) properties.getOrElse("quota.update.enabled", "false").toBoolean else true

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

  lazy val cloudFrontPrivateKeyLocation: String = "/etc/gu/ssl/private/cloudfront.pem"
  lazy val cloudFrontDomainImageBucket: Option[String] = properties.get("cloudfront.domain.imagebucket")
  lazy val cloudFrontDomainThumbBucket: Option[String] = properties.get("cloudfront.domain.thumbbucket")
  lazy val cloudFrontKeyPairId: Option[String]         = properties.get("cloudfront.keypair.id")

  lazy val topicArn: String = configuration.get[String]("sns.topic.arn")

  lazy val requiredMetadata = List("credit", "description", "usageRights")

  lazy val persistenceIdentifier: String = configuration.get[String]("persistence.identifier")
  lazy val queriableIdentifiers = Seq(persistenceIdentifier)

  lazy val persistedRootCollections: List[String] = properties.get("persistence.collections") match {
    case Some(collections) => collections.split(',').toList
    case None => List("GNM Archive")
  }

  def convertToInt(s: String): Option[Int] = Try { s.toInt }.toOption
}
