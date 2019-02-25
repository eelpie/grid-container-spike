package lib

import com.gu.mediaservice.lib.config.CommonConfig
import play.api.Configuration

class KahunaConfig(override val configuration: Configuration) extends CommonConfig {

  final override lazy val appName = "kahuna"

  val sentryDsn: Option[String] = configuration.getOptional[String]("sentry.dsn").filterNot(_.isEmpty)

  val thumbOrigin: String = configuration.get[String]("origin.thumb")
  val fullOrigin: String = configuration.get[String]("origin.full")
  val cropOrigin: String = configuration.get[String]("origin.crops")
  val imageOrigin: String = configuration.get[String]("origin.images")

  val googleTrackingId: Option[String] = configuration.getOptional[String]("google.tracking.id").filterNot(_.isEmpty)
}
