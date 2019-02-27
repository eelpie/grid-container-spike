package lib

import com.gu.mediaservice.lib.config.CommonConfig
import play.api.Configuration

class LeasesConfig(override val configuration: Configuration) extends CommonConfig {

  final override lazy val appName = "leases"

  val topicArn = configuration.getOptional[String]("sns.topic.arn")

  val leasesTable = configuration.get[String]("dynamo.tablename.leasesTable")

}
