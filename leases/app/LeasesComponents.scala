import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.play.GridComponents
import controllers.MediaLeaseController
import lib.{LeaseNotifier, LeaseStore, LeasesConfig}
import play.api.ApplicationLoader.Context
import router.Routes

class LeasesComponents(context: Context) extends GridComponents(context) {
  final override lazy val config = new LeasesConfig(configuration)
  val services = new Services(config)

  val store = new LeaseStore(config)
  val notifications = new LeaseNotifier(config, store)

  val controller = new MediaLeaseController(auth, store, config, notifications, controllerComponents, services)
  override lazy val router = new Routes(httpErrorHandler, controller, management)
}
