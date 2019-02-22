package controllers

import com.gu.mediaservice.lib.argo.ArgoHelpers
import com.gu.mediaservice.lib.auth.Authentication
import com.gu.mediaservice.model.Agencies
import lib._
import lib.elasticsearch.ElasticSearchVersion
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}


class UsageController(auth: Authentication, config: MediaApiConfig, elasticSearch: ElasticSearchVersion, usageQuota: Option[UsageQuota],
                      override val controllerComponents: ControllerComponents)(implicit val ec: ExecutionContext)
  extends BaseController with ArgoHelpers {

  val numberOfDaysInPeriod = 30

  def bySupplier = auth.async { request =>
    implicit val r = request

    Future.sequence(
      Agencies.all.keys.map(elasticSearch.usageForSupplier(_, numberOfDaysInPeriod)))
        .map(_.toList)
        .map((s: List[SupplierUsageSummary]) => respond(s))
        .recover {
          case e => respondError(InternalServerError, "unknown-error", e.toString)
        }
  }

  def forSupplier(id: String) = auth.async { request =>
    implicit val r = request

    elasticSearch.usageForSupplier(id, numberOfDaysInPeriod)
      .map((s: SupplierUsageSummary) => respond(s))
      .recover {
        case e => respondError(InternalServerError, "unknown-error", e.toString)
      }

  }

  def quotaForImage(id: String) = auth.async { request =>
    implicit val r = request

    usageQuota.map { usageQuota =>
      usageQuota.usageStatusForImage(id)
        .map((u: UsageStatus) => respond(u))
        .recover {
          case e: ImageNotFound => respondError(NotFound, "image-not-found", e.toString)
          case e => respondError(InternalServerError, "unknown-error", e.toString)
        }
    }.getOrElse {
      Future.successful(NotImplemented)
    }
  }

  def quotas = auth.async { request =>
    usageQuota.map { usageQuota =>
      usageQuota.usageStore.getUsageStatus()
        .map((s: StoreAccess) => respond(s))
        .recover {
          case e => respondError(InternalServerError, "unknown-error", e.toString)
        }
    }.getOrElse {
      Future.successful(NotImplemented)
    }
  }

}
