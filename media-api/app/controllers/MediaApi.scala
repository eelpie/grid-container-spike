package controllers

import java.net.URI

import akka.stream.scaladsl.StreamConverters
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.gu.mediaservice.lib.argo._
import com.gu.mediaservice.lib.argo.model._
import com.gu.mediaservice.lib.auth.Authentication.{AuthenticatedService, PandaUser, Principal}
import com.gu.mediaservice.lib.auth._
import com.gu.mediaservice.lib.aws.{MessageSender, UpdateMessage}
import com.gu.mediaservice.lib.cleanup.{MetadataCleaners, SupplierProcessors}
import com.gu.mediaservice.lib.config.{MetadataConfig, Services}
import com.gu.mediaservice.lib.formatting.printDateTime
import com.gu.mediaservice.lib.logging.GridLogger
import com.gu.mediaservice.lib.metadata.ImageMetadataConverter
import com.gu.mediaservice.model._
import com.gu.permissions.PermissionDefinition
import lib._
import lib.elasticsearch._
import lib.imagebuckets.S3ImageBucket
import org.http4s.UriTemplate
import org.joda.time.DateTime
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class MediaApi(
                auth: Authentication,
                messageSender: MessageSender,
                elasticSearch: ElasticSearchVersion,
                imageResponse: ImageResponse,
                val config: MediaApiConfig,
                override val controllerComponents: ControllerComponents,
                imageS3Client: S3ImageBucket,
                mediaApiMetrics: MediaApiMetrics,
                services: Services,
                permissionsHandler: PermissionsHandler,
                ws: WSClient
              )(implicit val ec: ExecutionContext) extends BaseController with ArgoHelpers {

  private val searchParamList = List("q", "ids", "offset", "length", "orderBy",
    "since", "until", "modifiedSince", "modifiedUntil", "takenSince", "takenUntil",
    "uploadedBy", "archived", "valid", "free", "payType",
    "hasExports", "hasIdentifier", "missingIdentifier", "hasMetadata",
    "persisted", "usageStatus", "usagePlatform", "hasRightsAcquired", "syndicationStatus").mkString(",")

  private val searchLinkHref = s"${services.apiBaseUri}/images{?$searchParamList}"

  private val searchLink = Link("search", searchLinkHref)

  private val indexResponse = {
    val indexData = Json.obj(
      "description" -> "This is the Media API"
      // ^ Flatten None away
    )
    val indexLinks = List(
      searchLink,
      Link("image",           s"${services.apiBaseUri}/images/{id}"),
      // FIXME: credit is the only field available for now as it's the only on
      // that we are indexing as a completion suggestion
      Link("metadata-search", s"${services.apiBaseUri}/suggest/metadata/{field}{?q}"),
      Link("label-search",    s"${services.apiBaseUri}/images/edits/label{?q}"),
      Link("cropper",         services.cropperBaseUri),
      Link("loader",          services.loaderBaseUri),
      Link("edits",           services.metadataBaseUri),
      Link("session",         s"${services.apiBaseUri}/session"),
      Link("witness-report",  s"${services.guardianWitnessBaseUri}/2/report/{id}"),
      Link("collections",     services.collectionsBaseUri),
      Link("permissions",     s"${services.apiBaseUri}/permissions"),
      Link("leases",          services.leasesBaseUri)
    )
    respond(indexData, indexLinks)
  }

  private val ImageCannotBeDeleted = respondError(MethodNotAllowed, "cannot-delete", "Cannot delete persisted images")
  private val ImageDeleteForbidden = respondError(Forbidden, "delete-not-allowed", "No permission to delete this image")
  private val ImageEditForbidden = respondError(Forbidden, "edit-not-allowed", "No permission to edit this image")
  private def ImageNotFound(id: String) = respondError(NotFound, "image-not-found", s"No image found with the given id $id")
  private val ExportNotFound = respondError(NotFound, "export-not-found", "No export found with the given id")

  def index = auth { indexResponse }

  def getIncludedFromParams(request: AuthenticatedRequest[AnyContent, Principal]): List[String] = {
    val includedQuery: Option[String] = request.getQueryString("include")

    includedQuery.map(_.split(",").map(_.trim).toList).getOrElse(List())
  }

  private def isUploaderOrHasPermission(
    request: AuthenticatedRequest[AnyContent, Principal],
    image: Image,
    permission: PermissionDefinition
  ) = {
    request.user match {
      case user: PandaUser =>
        if (user.user.email.toLowerCase == image.uploadedBy.toLowerCase) {
          true
        } else {
          permissionsHandler.hasPermission(user, permission)
        }
      case _: AuthenticatedService => true
      case _ => false
    }
  }

  def canUserWriteMetadata(request: AuthenticatedRequest[AnyContent, Principal], image: Image): Boolean = {
    isUploaderOrHasPermission(request, image, Permissions.EditMetadata)
  }

  def canUserDeleteImage(request: AuthenticatedRequest[AnyContent, Principal], image: Image): Boolean = {
    isUploaderOrHasPermission(request, image, Permissions.DeleteImage)
  }

  private def isAvailableForSyndication(image: Image): Boolean = image.syndicationRights.exists(_.isAvailableForSyndication)

  private def hasPermission(request: Authentication.Request[Any], image: Image): Boolean = request.user.apiKey.tier match {
    case Syndication => isAvailableForSyndication(image)
    case _ => true
  }

  def reindex = auth.async { request =>
      imageS3Client.listAll.map{ images =>
        Logger.info("List all got " + images.size + " images")
        images.foreach{ i =>
          Logger.info("Found image: " + i.uri)
          // Stream and post to image loader for ingesting

          val s3Object = imageS3Client.getImage(i.uri)
          Logger.info("Got S3 object for image: " + s3Object + " " + s3Object.getObjectMetadata)

          val contentSource = StreamConverters.fromInputStream(() => s3Object.getObjectContent)
          val contentLength = s3Object.getObjectMetadata.getContentLength

          val mediaApiKey = auth.keyStore.findKey("cropper").getOrElse(throw new Error("Missing cropper API key in key bucket"))  // TODO

          Logger.info("Posting to image loader: " + contentLength)
          ws.url("http://image-loader.default.svc.cluster.local:9003/images").
            addHttpHeaders(("Content-Length", contentLength.toString)).
            addHttpHeaders(("X-Gu-Media-Key", mediaApiKey)).

            post(contentSource).
            map { r =>
            Logger.info("Image loader response: + " + r.status + ": " + r.body)
          }
        }
        Ok
      }
  }

  def getImage(id: String) = auth.async { request =>
    implicit val r = request

    val include = getIncludedFromParams(request)

    elasticSearch.getImageById(id) map {
      case Some(source) if hasPermission(request, source) =>
        val writePermission = canUserWriteMetadata(request, source)
        val deletePermission = canUserDeleteImage(request, source)

        val (imageData, imageLinks, imageActions) =
          imageResponse.create(id, source, writePermission, deletePermission, include, request.user.apiKey.tier)

        respond(imageData, imageLinks, imageActions)

      case _ => ImageNotFound(id)
    }
  }

  def getImageFileMetadata(id: String) = auth.async { request =>
    implicit val r = request

    elasticSearch.getImageById(id) map {
      case Some(image) if hasPermission(request, image) =>
        val links = List(
          Link("image", s"${services.apiBaseUri}/images/$id")
        )
        respond(Json.toJson(image.fileMetadata), links)
      case _ => ImageNotFound(id)
    }
  }

  def getImageExports(id: String) = auth.async { request =>
    implicit val r = request

    elasticSearch.getImageById(id) map {
      case Some(image) if hasPermission(request, image) =>
        val links = List(
          Link("image", s"${services.apiBaseUri}/images/$id")
        )
        respond(Json.toJson(image.exports), links)
      case _ => ImageNotFound(id)
    }
  }

  def getImageExport(imageId: String, exportId: String) = auth.async { request =>
    implicit val r = request

    elasticSearch.getImageById(imageId) map {
      case Some(source) if hasPermission(request, source) =>
        val exportOption = source.exports.find(_.id.contains(exportId))
        exportOption.foldLeft(ExportNotFound)((memo, export) => respond(export))
      case _ => ImageNotFound(imageId)
    }

  }

  def deleteImage(id: String) = auth.async { request =>
    implicit val r = request

    elasticSearch.getImageById(id) map {
      case Some(image) if hasPermission(request, image) =>
        val imageCanBeDeleted = imageResponse.canBeDeleted(image)

        if (imageCanBeDeleted) {
          val canDelete = canUserDeleteImage(request, image)

          if (canDelete) {
            val updateMessage = UpdateMessage(subject = "delete-image", id = Some(id))
            messageSender.publish(updateMessage)
            Accepted
          } else {
            ImageDeleteForbidden
          }
        } else {
          ImageCannotBeDeleted
        }

      case _ => ImageNotFound(id)
    }
  }

  def downloadOriginalImage(id: String) = auth.async { request =>
    implicit val r = request

    elasticSearch.getImageById(id) flatMap {
      case Some(image) if hasPermission(request, image) => {
        val apiKey = request.user.apiKey
        GridLogger.info(s"Download original image $id", apiKey, id)
        mediaApiMetrics.incrementOriginalImageDownload(apiKey)
        val s3Object = imageS3Client.getObject(config.imageBucket, image.source.file)
        val file = StreamConverters.fromInputStream(() => s3Object.getObjectContent)
        val entity = HttpEntity.Streamed(file, image.source.size, image.source.mimeType)

        Future.successful(
          Result(ResponseHeader(OK), entity).withHeaders("Content-Disposition" -> imageS3Client.getContentDisposition(image))
        )
      }
      case _ => Future.successful(ImageNotFound(id))
    }
  }

  def reindexImage(id: String) = auth.async { request =>
    implicit val r = request

    val metadataCleaners = new MetadataCleaners(MetadataConfig.allPhotographersMap)
    elasticSearch.getImageById(id) map {
      case Some(image) if hasPermission(request, image) =>
        // TODO: apply rights to edits API too
        // TODO: helper to abstract boilerplate
        val canWrite = canUserWriteMetadata(request, image)
        if (canWrite) {
          val imageMetadata = ImageMetadataConverter.fromFileMetadata(image.fileMetadata)
          val cleanMetadata = metadataCleaners.clean(imageMetadata)
          val imageCleanMetadata = image.copy(metadata = cleanMetadata, originalMetadata = cleanMetadata)
          val processedImage = SupplierProcessors.process(imageCleanMetadata)

          // FIXME: dirty hack to sync the originalUsageRights and originalMetadata as well
          val finalImage = processedImage.copy(
            originalMetadata    = processedImage.metadata,
            originalUsageRights = processedImage.usageRights
          )

          val notification = Json.toJson(finalImage)

          val updateMessage = UpdateMessage(subject = "update-image", id = Some(finalImage.id), image = Some(finalImage))
          messageSender.publish(updateMessage)

          Ok(Json.obj(
            "id" -> id,
            "changed" -> JsBoolean(image != finalImage),
            "data" -> Json.obj(
              "oldImage" -> image,
              "updatedImage" -> finalImage
            )
          ))
        } else {
          ImageEditForbidden
        }
      case None => ImageNotFound(id)
    }
  }

  def imageSearch() = auth.async { request =>
    implicit val r = request

    val include = getIncludedFromParams(request)

    def hitToImageEntity(elasticId: String, image: Image): EmbeddedEntity[JsValue] = {
      val writePermission = canUserWriteMetadata(request, image)
      val deletePermission = canUserDeleteImage(request, image)

      val (imageData, imageLinks, imageActions) =
        imageResponse.create(elasticId, image, writePermission, deletePermission, include, request.user.apiKey.tier)
      val id = (imageData \ "id").as[String]
      val imageUri = URI.create(s"${services.apiBaseUri}/images/$id")
      EmbeddedEntity(uri = imageUri, data = Some(imageData), imageLinks, imageActions)
    }

    def respondSuccess(searchParams: SearchParams) = for {
      SearchResults(hits, totalCount) <- elasticSearch.search(searchParams)
      imageEntities = hits map (hitToImageEntity _).tupled
      prevLink = getPrevLink(searchParams)
      nextLink = getNextLink(searchParams, totalCount)
      links = List(prevLink, nextLink).flatten
    } yield respondCollection(imageEntities, Some(searchParams.offset), Some(totalCount), links)

    val searchParams = SearchParams(request)

    SearchParams.validate(searchParams).fold(
      // TODO: respondErrorCollection?
      errors => Future.successful(respondError(UnprocessableEntity, InvalidUriParams.errorKey,
        // Annoyingly `NonEmptyList` and `IList` don't have `mkString`
        errors.map(_.message).list.reduce(_+ ", " +_), List(searchLink))
      ),
      params => respondSuccess(params)
    )
  }

  private def getSearchUrl(searchParams: SearchParams, updatedOffset: Int, length: Int): String = {
    // Enforce a toDate to exclude new images since the current request
    val toDate = searchParams.until.getOrElse(DateTime.now)

    val paramMap: Map[String, String] = SearchParams.toStringMap(searchParams) ++ Map(
      "offset" -> updatedOffset.toString,
      "length" -> length.toString,
      "toDate" -> printDateTime(toDate)
    )

    paramMap.foldLeft(UriTemplate()){ (acc, pair) => acc.expandAny(pair._1, pair._2)}.toString
  }

  private def getPrevLink(searchParams: SearchParams): Option[Link] = {
    val prevOffset = List(searchParams.offset - searchParams.length, 0).max
    if (searchParams.offset > 0) {
      // adapt length to avoid overlapping with current
      val prevLength = List(searchParams.length, searchParams.offset - prevOffset).min
      val prevUrl = getSearchUrl(searchParams, prevOffset, prevLength)
      Some(Link("prev", prevUrl))
    } else {
      None
    }
  }

  private def getNextLink(searchParams: SearchParams, totalCount: Long): Option[Link] = {
    val nextOffset = searchParams.offset + searchParams.length
    if (nextOffset < totalCount) {
      val nextUrl = getSearchUrl(searchParams, nextOffset, searchParams.length)
      Some(Link("next", nextUrl))
    } else {
      None
    }
  }

}
