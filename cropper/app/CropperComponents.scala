import com.gu.mediaservice.lib.auth.{GrantAllPermissionsHandler, GuardianEditorialPermissionsHandler}
import com.gu.mediaservice.lib.aws.{MessageSenderVersion, SNS}
import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.imaging.ImageOperations
import com.gu.mediaservice.lib.management.ManagementWithPermissions
import com.gu.mediaservice.lib.play.GridComponents
import controllers.CropperController
import lib.{CropStore, CropperConfig, Crops, Notifications}
import play.api.ApplicationLoader.Context
import play.api.Logger
import router.Routes

class CropperComponents(context: Context) extends GridComponents(context) {
  final override lazy val config = new CropperConfig(configuration)

  val services = new Services(config)

  val store = new CropStore(config)
  val imageOperations = new ImageOperations(context.environment.rootPath.getAbsolutePath)

  val publishers: Seq[MessageSenderVersion] = Seq(
    new SNS(config, config.topicArn)
  )

  val crops = new Crops(config, store, imageOperations)
  val notifications = new Notifications(publishers)

  val permissionsHandler = (for {
    permissionsBucket <- config.permissionsBucket
    permissionsStage <- config.permissionsStage
  } yield {
    new GuardianEditorialPermissionsHandler(permissionsBucket, permissionsStage, config)
  }).getOrElse{
    Logger.warn("No permissions handler is configured; granting all permissions to all users.")
    new GrantAllPermissionsHandler()
  }

  val controller = new CropperController(auth, crops, store, notifications, config, controllerComponents, services, permissionsHandler)
  val permissionsAwareManagement = new ManagementWithPermissions(controllerComponents, permissionsHandler)

  override lazy val router = new Routes(httpErrorHandler, controller, permissionsAwareManagement)
}
