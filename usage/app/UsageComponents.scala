import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.lib.aws.{Kinesis, MessageSenderVersion, SNS}
import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.play.GridComponents
import controllers.UsageApi
import lib._
import model._
import play.api.ApplicationLoader.Context
import play.api.Logger
import router.Routes

import scala.concurrent.Future

class UsageComponents(context: Context) extends GridComponents(context) {

  final override lazy val config = new UsageConfig(configuration)

  // TODO try to remove lazy vals where possible
  // Note: had to make these lazy to avoid init order problems
  lazy val services = new Services(config)

  val publishers: Seq[MessageSenderVersion] = Seq(
    config.topicArn.map(topicArn => new SNS(config, topicArn)),
    config.thrallKinesisStream.map(kinesisStreamName => new Kinesis(config, kinesisStreamName))
  ).flatten

  val usageMetadataBuilder = new UsageMetadataBuilder(config)
  val mediaWrapper = new MediaWrapperOps(usageMetadataBuilder)
  val mediaUsage = new MediaUsageOps(usageMetadataBuilder)
  val liveContentApi = new LiveContentApi(config)
  val usageGroup = new UsageGroupOps(config, mediaUsage, liveContentApi, mediaWrapper, services)
  val usageTable = new UsageTable(config, mediaUsage)
  val usageNotifier = new UsageNotifier(publishers, usageTable)
  val usageStream = new UsageStream(usageGroup)

  val usageMetrics = config.cloudWatchNamespace.map{ ns =>
    new CloudWatchUsageMetrics(ns, config.withAWSCredentials)
  }.getOrElse{
    Logger.info("CloudWatch metrics are not configured.")
    new NullUsageMetrics
  }

  val usageRecorder = new UsageRecorder(usageMetrics, usageTable, usageStream, usageNotifier, usageNotifier)
  val notifications = new Notifications(publishers)

  if(!config.apiOnly) {
    val crierReader = new CrierStreamReader(config)
    crierReader.start()
  }

  usageRecorder.start()
  context.lifecycle.addStopHook(() => {
    usageRecorder.stop()
    Future.successful(())
  })

  val auth = new Authentication(config, services, actorSystem, defaultBodyParser, wsClient, controllerComponents, executionContext)

  val controller = new UsageApi(auth, usageTable, usageGroup, notifications, config, usageRecorder, liveContentApi,
    controllerComponents, playBodyParsers, services)

  override lazy val router = new Routes(httpErrorHandler, controller, management)
}
