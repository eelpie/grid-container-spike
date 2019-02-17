package auth

import com.gu.mediaservice.lib.config.{CommonConfig, Services}
import play.api.Configuration

import scala.concurrent.ExecutionContext

class AuthConfig(override val configuration: Configuration)(implicit ec: ExecutionContext) extends CommonConfig {

  override lazy val appName = "auth"

  val services = new Services(this)

  val rootUri: String = services.authBaseUri
  val mediaApiUri: String = services.apiBaseUri
  val kahunaUri = services.kahunaBaseUri
}
