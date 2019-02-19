package lib

import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.gu.mediaservice.lib.config.CommonConfig
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.{Configuration, Logger}

class ThrallConfig(override val configuration: Configuration) extends CommonConfig {
  Logger.info("Configuring thrall")
  final override lazy val appName = "thrall"

  lazy val elasticsearchPort: Option[Int] = configuration.getOptional[Int]("es.port")
  lazy val elasticsearchCluster: Option[String] = configuration.getOptional[String]("es.cluster")

  lazy val elasticsearch6Host: Option[String] = configuration.getOptional[String]("es6.host")
  lazy val elasticsearch6Port: Option[Int] = configuration.getOptional[Int]("es6.port")
  lazy val elasticsearch6Cluster: Option[String] = configuration.getOptional[String]("es6.cluster")
  lazy val elasticsearch6Shards: Option[Int] = configuration.getOptional[Int](("es6.shards"))
  lazy val elasticsearch6Replicas: Option[Int] = configuration.getOptional[Int](("es6.replicas"))

  lazy val elasticsearchHost: Option[String] = configuration.getOptional[String]("es.host")

  lazy val queueUrl: String = configuration.get[String]("sqs.queue.url")

  private lazy val ec2Client = withAWSCredentials(AmazonEC2ClientBuilder.standard()).build()

  lazy val imageBucket: String = properties("s3.image.bucket")

  lazy val writeAlias: String = properties.getOrElse("es.index.aliases.write", configuration.get[String]("es.index.aliases.write"))

  lazy val thumbnailBucket: String = properties("s3.thumb.bucket")

  lazy val healthyMessageRate: Int = properties("sqs.message.min.frequency").toInt

  lazy val dynamoTopicArn: String = properties("indexed.image.sns.topic.arn")

  lazy val topicArn: String = properties("sns.topic.arn")

  lazy val from: Option[DateTime] = properties.get("rewind.from").map(ISODateTimeFormat.dateTime.parseDateTime)

}
