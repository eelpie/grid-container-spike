package auth

import com.gu.mediaservice.lib.config.{CommonConfig, Services}
import play.api.Configuration

import scala.concurrent.ExecutionContext

class AuthConfig(override val configuration: Configuration)(implicit ec: ExecutionContext) extends CommonConfig {
  override lazy val appName = "auth"
}
