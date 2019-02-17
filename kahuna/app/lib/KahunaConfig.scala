package lib

import com.gu.mediaservice.lib.config.{CommonConfig, Services}
import play.api.Configuration

class KahunaConfig(override val configuration: Configuration) extends CommonConfig {

  final override lazy val appName = "kahuna"

  val services = new Services(this.domainRoot, this.isProd)

  val rootUri: String = services.kahunaBaseUri
  val mediaApiUri: String = services.apiBaseUri
  val authUri: String = services.authBaseUri

  val sentryDsn: Option[String] = properties.get("sentry.dsn").filterNot(_.isEmpty)

  val thumbOrigin: String = properties("origin.thumb")
  val fullOrigin: String = properties("origin.full")
  val cropOrigin: String = properties("origin.crops")
  val imageOrigin: String = properties("origin.images")
  val googleTrackingId: Option[String] = properties.get("google.tracking.id").filterNot(_.isEmpty)
}
