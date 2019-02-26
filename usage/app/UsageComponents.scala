import com.gu.mediaservice.lib.aws.MessageSenderVersion
import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.play.GridComponents
import controllers.UsageApi
import lib._
import model._
import play.api.ApplicationLoader.Context
import router.Routes
import com.gu.mediaservice.lib.aws.SNS

import scala.concurrent.Future

class UsageComponents(context: Context) extends GridComponents(context) {

  final override lazy val config = new UsageConfig(configuration)

  // TODO try to remove lazy vals where possible
  // Note: had to make these lazy to avoid init order problems
  lazy val services = new Services(config)

  val publishers: Seq[MessageSenderVersion] = Seq(
    new SNS(config, config.topicArn)
  )

  val usageMetadataBuilder = new UsageMetadataBuilder(config)
  val mediaWrapper = new MediaWrapperOps(usageMetadataBuilder)
  val mediaUsage = new MediaUsageOps(usageMetadataBuilder)
  val liveContentApi = new LiveContentApi(config)
  val usageGroup = new UsageGroupOps(config, mediaUsage, liveContentApi, mediaWrapper, services)
  val usageTable = new UsageTable(config, mediaUsage)
  val usageMetrics = new UsageMetrics(config)
  val usageNotifier = new UsageNotifier(publishers, usageTable)
  val usageStream = new UsageStream(usageGroup)
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

  val controller = new UsageApi(auth, usageTable, usageGroup, notifications, config, usageRecorder, liveContentApi,
    controllerComponents, playBodyParsers, services)

  override lazy val router = new Routes(httpErrorHandler, controller, management)
}
