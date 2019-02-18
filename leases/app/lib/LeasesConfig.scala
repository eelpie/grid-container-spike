package lib

import java.net.URI

import com.gu.mediaservice.lib.config.CommonConfig
import play.api.Configuration

class LeasesConfig(override val configuration: Configuration) extends CommonConfig {

  final override lazy val appName = "leases"

  val topicArn = properties("sns.topic.arn")

  val leasesTable = properties("dynamo.tablename.leasesTable")

  private def uri(u: String) = URI.create(u)

}
