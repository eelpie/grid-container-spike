package auth

import com.gu.mediaservice.lib.auth.{GrantAllPermissionsHandler, GuardianEditorialPermissionsHandler, PermissionsHandler}
import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.lib.management.ManagementWithPermissions
import com.gu.mediaservice.lib.play.GridComponents
import play.api.ApplicationLoader.Context
import play.api.Logger
import router.Routes

class AuthComponents(context: Context) extends GridComponents(context) {
  final override lazy val config = new AuthConfig(configuration)
  lazy val services = new Services(config)

  val permissionsHandler = (for {
    permissionsBucket <- config.permissionsBucket
    permissionsStage <- config.permissionsStage
  } yield {
    new GuardianEditorialPermissionsHandler(permissionsBucket, permissionsStage, config)
  }).getOrElse{
    Logger.warn("No permissions handler is configured; granting all permissions to all users.")
    new GrantAllPermissionsHandler()
  }

  val controller = new AuthController(auth, config, controllerComponents, services, permissionsHandler)
  val permissionsAwareManagement = new ManagementWithPermissions(controllerComponents, permissionsHandler)

  override val router = new Routes(httpErrorHandler, controller, permissionsAwareManagement)
}