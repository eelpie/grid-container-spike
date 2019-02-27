import com.gu.mediaservice.lib.aws.{MessageSenderVersion, SNS}
import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.play.GridComponents
import controllers.{CollectionsController, ImageCollectionsController}
import lib.{CollectionsConfig, CollectionsMetrics, Notifications}
import play.api.ApplicationLoader.Context
import router.Routes
import store.CollectionsStore

class CollectionsComponents(context: Context) extends GridComponents(context) {
  final override lazy val config = new CollectionsConfig(configuration)

  val services = new Services(config)

  val publishers: Seq[MessageSenderVersion] = Seq(
    config.topicArn.map(topicArn => new SNS(config, topicArn))
  ).flatten

  val store = new CollectionsStore(config)
  val metrics = new CollectionsMetrics(config)
  val notifications = new Notifications(publishers)

  val collections = new CollectionsController(auth, config, store, controllerComponents, services)
  val imageCollections = new ImageCollectionsController(auth, config, notifications, controllerComponents)

  override val router = new Routes(httpErrorHandler, collections, imageCollections, management)
}
