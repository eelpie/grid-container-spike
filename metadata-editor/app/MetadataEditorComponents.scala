import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.aws.{Kinesis, MessageSenderVersion, SNS}
import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.play.GridComponents
import controllers.{EditsApi, EditsController}
import lib._
import play.api.ApplicationLoader.Context
import play.api.Logger
import router.Routes

class MetadataEditorComponents(context: Context) extends GridComponents(context) {
  final override lazy val config = new EditsConfig(configuration)
  lazy val services = new Services(config)

  val publishers: Seq[MessageSenderVersion] = Seq(
    config.topicArn.map(topicArn => new SNS(config, topicArn)),
    config.thrallKinesisStream.map(kinesisStreamName => new Kinesis(config, kinesisStreamName))
  ).flatten

  val store = new EditsStore(config)
  val notifications = new Notifications(publishers)
  val imageOperations = new ImageOperations(context.environment.rootPath.getAbsolutePath)

  val metrics = config.cloudWatchNamespace.map { ns =>
    new CloudWatchMetadataEditorMetrics(ns, config.withAWSCredentials)
  }.getOrElse {
    Logger.info("CloudWatch metrics are not configured.")
    new NullMetadataEditorMetrics
  }

  val messageConsumer = new MetadataMessageConsumer(config, metrics, store)

  messageConsumer.startSchedule()
  context.lifecycle.addStopHook {
    () => messageConsumer.actorSystem.terminate()
  }

  val auth = new Authentication(config, services, actorSystem, defaultBodyParser, wsClient, controllerComponents, executionContext)
  val editsController = new EditsController(auth, store, notifications, config, controllerComponents, services)
  val controller = new EditsApi(auth, config, controllerComponents, services)

  override val router = new Routes(httpErrorHandler, controller, editsController, management)
}
