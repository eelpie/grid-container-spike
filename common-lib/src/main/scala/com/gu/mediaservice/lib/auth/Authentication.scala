package com.gu.mediaservice.lib.auth

import akka.actor.ActorSystem
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.gu.mediaservice.lib.argo.ArgoHelpers
import com.gu.mediaservice.lib.argo.model.Link
import com.gu.mediaservice.lib.auth.Authentication.{AuthenticatedService, PandaUser}
import com.gu.mediaservice.lib.config.{CommonConfig, Services}
import com.gu.mediaservice.lib.logging.GridLogger
import com.gu.pandomainauth.PanDomainAuthSettingsRefresher
import com.gu.pandomainauth.action.{AuthActions, UserRequest}
import com.gu.pandomainauth.model.{AuthenticatedUser, User}
import com.gu.pandomainauth.service.Google2FAGroupChecker
import play.api.Logger
import play.api.libs.ws.WSClient
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class Authentication(config: CommonConfig,
                     services: Services,
                     actorSystem: ActorSystem,
                     override val parser: BodyParser[AnyContent],
                     override val wsClient: WSClient,
                     override val controllerComponents: ControllerComponents,
                     override val executionContext: ExecutionContext)

  extends ActionBuilder[Authentication.Request, AnyContent] with AuthActions with ArgoHelpers {

  implicit val ec: ExecutionContext = executionContext

  val loginLinks = List(
    Link("login", services.loginUriTemplate)
  )

  // API key errors
  val invalidApiKeyResult    = respondError(Unauthorized, "invalid-api-key", "Invalid API key provided", loginLinks)

  private val headerKey = "X-Gu-Media-Key"
  private val keyStoreBucket = config.authKeyStoreBucket
  Logger.info("Init'ing keystore with bucket: " + keyStoreBucket)
  val keyStore = new KeyStore(keyStoreBucket, config)

  keyStore.scheduleUpdates(actorSystem.scheduler)

  override lazy val panDomainSettings: PanDomainAuthSettingsRefresher = buildPandaSettings()

  final override def authCallbackUrl: String = s"${services.authBaseUri}/oauthCallback"

  override def invokeBlock[A](request: Request[A], block: Authentication.Request[A] => Future[Result]): Future[Result] = {
    // Try to auth by API key, and failing that, with Panda
    request.headers.get(headerKey) match {
      case Some(key) =>
        keyStore.lookupIdentity(key) match {
          case Some(apiKey) =>
            GridLogger.info(s"Using api key with name ${apiKey.name} and tier ${apiKey.tier}", apiKey)
            if (ApiKey.hasAccess(apiKey, request, services))
              block(new AuthenticatedRequest(AuthenticatedService(apiKey), request))
            else
              Future.successful(ApiKey.unauthorizedResult)
          case None => Future.successful(invalidApiKeyResult)
        }
      case None =>
        APIAuthAction.invokeBlock(request, (userRequest: UserRequest[A]) => {
          block(new AuthenticatedRequest(PandaUser(userRequest.user), request))
        })
    }
  }

  private val userValidationEmailDomain = config.pandaUserDomain

  final override def validateUser(authedUser: AuthenticatedUser): Boolean = {
    Authentication.validateUser(authedUser, userValidationEmailDomain, multifactorChecker)
  }

  private def buildPandaSettings() = {
    new PanDomainAuthSettingsRefresher(
      domain = services.domainRoot,
      system = config.pandaSystem,
      bucketName = config.pandaSettingsBucket,
      settingsFileKey = "panda.settings",
      s3Client = config.withAWSCredentials(AmazonS3ClientBuilder.standard()).build()
    )
  }
}

object Authentication {
  sealed trait Principal { def apiKey: ApiKey }
  case class PandaUser(user: User) extends Principal { def apiKey: ApiKey = ApiKey(s"${user.firstName} ${user.lastName}", Internal) }
  case class AuthenticatedService(apiKey: ApiKey) extends Principal

  type Request[A] = AuthenticatedRequest[A, Principal]

  def getEmail(principal: Principal): String = principal match {
    case PandaUser(user) => user.email
    case _ => principal.apiKey.name
  }

  def validateUser(authedUser: AuthenticatedUser, userValidationEmailDomain: String, multifactorChecker: Option[Google2FAGroupChecker]): Boolean = {
    val isValidDomain = authedUser.user.email.endsWith("@" + userValidationEmailDomain)
    val passesMultifactor = if(multifactorChecker.nonEmpty) { authedUser.multiFactor } else { true }

    isValidDomain && passesMultifactor
  }
}
