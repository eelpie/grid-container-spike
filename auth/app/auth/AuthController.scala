package auth

import java.net.URI

import com.gu.mediaservice.lib.argo.ArgoHelpers
import com.gu.mediaservice.lib.argo.model.Link
import com.gu.mediaservice.lib.auth.Authentication.PandaUser
import com.gu.mediaservice.lib.auth.{Authentication, Permissions, PermissionsHandler}
import com.gu.mediaservice.lib.config.Services
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{BaseController, ControllerComponents}
import com.gu.pandomainauth.service.OAuthException

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class AuthController(auth: Authentication, val config: AuthConfig,
                     override val controllerComponents: ControllerComponents, services: Services, permissionsHandler: PermissionsHandler)(implicit ec: ExecutionContext)
  extends BaseController
  with ArgoHelpers {

  val indexResponse = {
    val indexData = Map("description" -> "This is the Auth API")
    val indexLinks = List(
      Link("root",          services.apiBaseUri),
      Link("login",         services.loginUriTemplate),
      Link("ui:logout",     s"${services.authBaseUri}/logout"),
      Link("session",       s"${services.authBaseUri}/session")
    )
    respond(indexData, indexLinks)
  }

  def index = auth.AuthAction { indexResponse }

  def session = auth.AuthAction { request =>
    val user = request.user
    val firstName = user.firstName
    val lastName = user.lastName

    val showPaid = permissionsHandler.hasPermission(PandaUser(request.user), Permissions.ShowPaid)

    respond(
      Json.obj("user" ->
        Json.obj(
          "name" -> s"$firstName $lastName",
          "firstName" -> firstName,
          "lastName" -> lastName,
          "email" -> user.email,
          "avatarUrl" -> user.avatarUrl,
          "permissions" ->
            Json.obj(
              "showPaid" -> showPaid
            )
        )
      )
    )
  }


  def isOwnDomainAndSecure(uri: URI): Boolean = {
    uri.getHost.endsWith(config.domainRoot) && uri.getScheme == "https"
  }
  def isValidDomain(inputUri: String): Boolean = {
    Try(URI.create(inputUri)).filter(isOwnDomainAndSecure).isSuccess
  }


  // Trigger the auth cycle
  // If a redirectUri is provided, redirect the browser there once auth'd,
  // else return a dummy page (e.g. for automatically re-auth'ing in the background)
  // FIXME: validate redirectUri before doing the auth
  def doLogin(redirectUri: Option[String] = None) = auth.AuthAction { req =>
    redirectUri map {
      case uri if isValidDomain(uri) => Redirect(uri)
      case _ => Ok("logged in (not redirecting to external redirectUri)")
    } getOrElse Ok("logged in")
  }

  def oauthCallback = Action.async { implicit request =>
    // We use the `Try` here as the `GoogleAuthException` are thrown before we
    // get to the asynchronicity of the `Future` it returns.
    // We then have to flatten the Future[Future[T]]. Fiddly...
    Future.fromTry(Try(auth.processOAuthCallback())).flatten.recover {
      // This is when session session args are missing
      case e: OAuthException => respondError(BadRequest, "google-auth-exception", e.getMessage, auth.loginLinks)

      // Class `missing anti forgery token` as a 4XX
      // see https://github.com/guardian/pan-domain-authentication/blob/master/pan-domain-auth-play_2-6/src/main/scala/com/gu/pandomainauth/service/GoogleAuth.scala#L63
      case e: IllegalArgumentException if e.getMessage == "The anti forgery token did not match" => {
        Logger.error(e.getMessage)
        respondError(BadRequest, "google-auth-exception", e.getMessage, auth.loginLinks)
      }
    }
  }

  def logout = Action { implicit request =>
    auth.processLogout
  }

}
