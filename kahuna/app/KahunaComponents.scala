import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.play.GridComponents
import controllers.{AssetsComponents, KahunaController}
import lib.KahunaConfig
import play.api.ApplicationLoader.Context
import play.api.{Configuration, Logger}
import play.filters.headers.SecurityHeadersConfig
import router.Routes

class KahunaComponents(context: Context) extends GridComponents(context) with AssetsComponents {
  final override lazy val config = new KahunaConfig(configuration)
  lazy val services = new Services(config)

  final override lazy val securityHeadersConfig: SecurityHeadersConfig = KahunaSecurityConfig(config, context.initialConfiguration, services)

  val auth = new Authentication(config, services, actorSystem, defaultBodyParser, wsClient, controllerComponents, executionContext)
  val controller = new KahunaController(auth, config, controllerComponents, services)
  final override val router = new Routes(httpErrorHandler, controller, assets, management)
}

object KahunaSecurityConfig {
  def apply(config: KahunaConfig, playConfig: Configuration, services: Services): SecurityHeadersConfig = {
    val base = SecurityHeadersConfig.fromConfiguration(playConfig)

    val serviceUrls = List(
      services.apiBaseUri,
      services.loaderBaseUri,
      services.cropperBaseUri,
      services.metadataBaseUri,
      services.imgopsBaseUri,
      services.usageBaseUri,
      services.collectionsBaseUri,
      services.leasesBaseUri,
      services.authBaseUri,
      services.guardianWitnessBaseUri
    )

    val frameSources = s"frame-src ${services.authBaseUri} ${services.kahunaBaseUri} https://accounts.google.com"
    val frameAncestors = s"frame-ancestors ${services.toolsDomains.map(domain => s"*.$domain").mkString(" ")}"
    val connectSources = s"connect-src ${(serviceUrls :+ config.imageOrigin).mkString(" ")} 'self' www.google-analytics.com"
    val imageSources = s"img-src data: blob: ${services.imgopsBaseUri} https://${config.fullOrigin} https://${config.thumbOrigin} ${config.cropOrigin} www.google-analytics.com 'self'"
    Logger.info("Content security policy image sources: " + imageSources)

    base.copy(
      // covered by frame-ancestors in contentSecurityPolicy
      frameOptions = None,
      // We use inline styles and script tags <sad face>
      contentSecurityPolicy = Some(s"$frameSources; $frameAncestors; $connectSources; $imageSources; default-src 'unsafe-inline' 'self'; script-src 'self' 'unsafe-inline' www.google-analytics.com;")
    )
  }
}
