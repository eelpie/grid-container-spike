package lib

import com.gu.mediaservice.lib.config.CommonConfig
import play.api.Configuration

class EditsConfig(override val configuration: Configuration) extends CommonConfig {

  final override lazy val appName = "metadata-editor"

  val keyStoreBucket: String = configuration.get[String]("auth.keystore.bucket")

  val editsTable: String = configuration.get[String]("dynamo.table.edits")

  val topicArn = configuration.getOptional[String]("sns.topic.arn")

  val queueUrl: String = configuration.get[String]("indexed.images.sqs.queue.url")

}
