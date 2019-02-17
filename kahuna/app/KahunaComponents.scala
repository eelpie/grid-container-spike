import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.play.GridComponents
import controllers.{AssetsComponents, KahunaController}
import lib.KahunaConfig
import play.api.ApplicationLoader.Context
import play.api.Configuration
import play.filters.headers.SecurityHeadersConfig
import router.Routes

class KahunaComponents(context: Context) extends GridComponents(context) with AssetsComponents {
  final override lazy val config = new KahunaConfig(configuration)
  val services = new Services(config.domainRoot, config.isProd)

  final override lazy val securityHeadersConfig: SecurityHeadersConfig = KahunaSecurityConfig(config, context.initialConfiguration, services)

  val controller = new KahunaController(auth, config, controllerComponents)
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

    base.copy(
      // covered by frame-ancestors in contentSecurityPolicy
      frameOptions = None,
      // We use inline styles and script tags <sad face>
      contentSecurityPolicy = Some(s"$frameSources; $frameAncestors; $connectSources; $imageSources; default-src 'unsafe-inline' 'self'; script-src 'self' 'unsafe-inline' www.google-analytics.com;")
    )
  }
}
