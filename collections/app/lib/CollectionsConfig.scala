package lib

import com.gu.mediaservice.lib.config.CommonConfig
import play.api.Configuration

import scala.concurrent.ExecutionContext

class CollectionsConfig(override val configuration: Configuration)(implicit ec: ExecutionContext) extends CommonConfig {

  override lazy val appName = "collections"

  val collectionsTable = configuration.get[String]("dynamo.table.collections")
  val imageCollectionsTable = configuration.get[String]("dynamo.table.imageCollections")
  val topicArn = configuration.getOptional[String]("sns.topic.arn")

}
