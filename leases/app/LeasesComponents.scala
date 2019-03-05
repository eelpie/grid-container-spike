import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.aws.{Kinesis, MessageSenderVersion, SNS}
import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.play.GridComponents
import controllers.MediaLeaseController
import lib.{LeaseNotifier, LeaseStore, LeasesConfig}
import play.api.ApplicationLoader.Context
import router.Routes

class LeasesComponents(context: Context) extends GridComponents(context) {
  final override lazy val config = new LeasesConfig(configuration)
  lazy val services = new Services(config)

  val publishers: Seq[MessageSenderVersion] = Seq(
    config.topicArn.map(topicArn => new SNS(config, topicArn)),
    config.thrallKinesisStream.map(kinesisStreamName => new Kinesis(config, kinesisStreamName))
  ).flatten

  val store = new LeaseStore(config)
  val notifications = new LeaseNotifier(publishers, store)

  val auth = new Authentication(config, services, actorSystem, defaultBodyParser, wsClient, controllerComponents, executionContext)
  val controller = new MediaLeaseController(auth, store, config, notifications, controllerComponents, services)
  override lazy val router = new Routes(httpErrorHandler, controller, management)
}
