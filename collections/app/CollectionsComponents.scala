import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.aws.{Kinesis, MessageSenderVersion, SNS}
import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.play.GridComponents
import controllers.{CollectionsController, ImageCollectionsController}
import lib.{CollectionsConfig, Notifications}
import play.api.ApplicationLoader.Context
import router.Routes
import store.CollectionsStore

class CollectionsComponents(context: Context) extends GridComponents(context) {
  final override lazy val config = new CollectionsConfig(configuration)

  lazy val services = new Services(config)

  val publishers: Seq[MessageSenderVersion] = Seq(
    config.topicArn.map(topicArn => new SNS(config, topicArn)),
    config.thrallKinesisStream.map(kinesisStreamName => new Kinesis(config, kinesisStreamName))
  ).flatten

  val store = new CollectionsStore(config)
  val notifications = new Notifications(publishers)

  val auth = new Authentication(config, services, actorSystem, defaultBodyParser, wsClient, controllerComponents, executionContext)
  val collections = new CollectionsController(auth, config, store, controllerComponents, services)
  val imageCollections = new ImageCollectionsController(auth, config, notifications, controllerComponents)

  override val router = new Routes(httpErrorHandler, collections, imageCollections, management)
}
