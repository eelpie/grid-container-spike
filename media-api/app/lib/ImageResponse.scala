package lib

import java.net.URI

import com.gu.mediaservice.lib.argo.model._
import com.gu.mediaservice.lib.auth.{Internal, Tier}
import com.gu.mediaservice.lib.collections.CollectionsManager
import com.gu.mediaservice.lib.config.Services
import com.gu.mediaservice.model._
import com.gu.mediaservice.model.usage._
import lib.imagebuckets.ImageBucket
import lib.usagerights.CostCalculator
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.utils.UriEncoding

import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Try}

class ImageResponse(config: MediaApiConfig, imageBucket: ImageBucket, thumbnailBucket: ImageBucket, usageQuota: Option[UsageQuota], services: Services) extends EditsResponse {
  implicit val usageQuotas = usageQuota

  object Costing extends CostCalculator {
    val quotas = usageQuotas
  }

  implicit val costing = Costing

  val metadataBaseUri: String = services.metadataBaseUri

  type FileMetadataEntity = EmbeddedEntity[FileMetadata]

  type UsageEntity = EmbeddedEntity[Usage]
  type UsagesEntity = EmbeddedEntity[List[UsageEntity]]

  type MediaLeaseEntity = EmbeddedEntity[MediaLease]
  type MediaLeasesEntity = EmbeddedEntity[LeasesByMedia]

  def hasPersistenceIdentifier(image: Image) =
    image.identifiers.contains(config.persistenceIdentifier)

  def hasExports(image: Image) =
    image.exports.nonEmpty

  def isArchived(image: Image) =
    image.userMetadata.exists(_.archived)

  def hasUsages(image: Image) =
    image.usages.nonEmpty

  def hasLeases(image: Image) =
    image.leases.leases.nonEmpty

  def hasPhotoshoot(image: Image): Boolean = image.userMetadata.exists(_.photoshoot.isDefined)

  def isInPersistedCollection(image: Image): Boolean = {
    // list of the first element of each collection's `path`, i.e all the root collections
    val collectionPaths: List[String] = image.collections.flatMap(_.path.headOption)

    // is image in at least one persisted collection?
    (collectionPaths diff config.persistedRootCollections).length < collectionPaths.length
  }

  def isPhotographerCategory[T <: UsageRights](usageRights: T) =
    usageRights match {
      case _:Photographer => true
      case _ => false
    }

  def isIllustratorCategory[T <: UsageRights](usageRights: T) =
    usageRights match {
      case _:Illustrator => true
      case _ => false
    }

  def isAgencyCommissionedCategory[T <: UsageRights](usageRights: T) =
    usageRights match {
      case _: CommissionedAgency => true
      case _ => false
    }

  def imagePersistenceReasons(image: Image): List[String] = {
    val reasons = ListBuffer[String]()

    if (hasPersistenceIdentifier(image))
      reasons += "persistence-identifier"

    if (hasExports(image))
      reasons += "exports"

    if (hasUsages(image))
      reasons += "usages"

    if (isArchived(image))
      reasons += "archived"

    if (isPhotographerCategory(image.usageRights))
      reasons += "photographer-category"

    if (isIllustratorCategory(image.usageRights))
      reasons += "illustrator-category"

    if (isAgencyCommissionedCategory(image.usageRights))
      reasons += CommissionedAgency.category

    if (hasLeases(image))
      reasons += "leases"

    if (isInPersistedCollection(image)) {
      reasons += "persisted-collection"
    }

    if (hasPhotoshoot(image)) {
      reasons += "photoshoot"
    }

    reasons.toList
  }

  def canBeDeleted(image: Image) = ! hasExports(image) && ! hasUsages(image)

  def create(id: String, image: Image, withWritePermission: Boolean,
             withDeletePermission: Boolean, included: List[String] = List(), tier: Tier):
            (JsValue, List[Link], List[Action]) = {

    val source = Try {
      Json.toJson(image)(imageResponseWrites(image.id, included.contains("fileMetadata")))
    }.recoverWith {
      case e =>
        Logger.error(s"Failed to read ElasticSearch response $id into Image object: ${e.getMessage}")
        Failure(e)
    }.get

    val pngFileUri = image.optimisedPng.map(_.file)

    val fileUri = image.source.file

    val imageUrl: String = imageBucket.signedUrlFor(fileUri, image)
    val pngUrl: Option[String] = pngFileUri.map(thumbnailBucket.signedUrlFor(_, image))

    val thumbUrl = thumbnailBucket.signedUrlFor(fileUri, image)

    val validityMap       = ImageExtras.validityMap(image, withWritePermission)
    val valid             = ImageExtras.isValid(validityMap)
    val invalidReasons    = ImageExtras.invalidReasons(validityMap)

    val persistenceReasons = imagePersistenceReasons(image)
    val isPersisted = persistenceReasons.nonEmpty

    val data = source.transform(addSecureSourceUrl(imageUrl))
      .flatMap(_.transform(wrapUserMetadata(id)))
      .flatMap(_.transform(addSecureThumbUrl(thumbUrl)))
      .flatMap(_.transform(
        pngUrl
          .map(url => addSecureOptimisedPngUrl(url))
          .getOrElse(__.json.pick)
        ))
      .flatMap(_.transform(addValidity(valid)))
      .flatMap(_.transform(addInvalidReasons(invalidReasons)))
      .flatMap(_.transform(addUsageCost(source)))
      .flatMap(_.transform(addPersistedState(isPersisted, persistenceReasons)))
      .flatMap(_.transform(addSyndicationStatus(image))).get

    val links: List[Link] = tier match {
      case Internal => imageLinks(id, imageUrl, pngUrl, withWritePermission, valid)
      case _ => List(downloadLink(id))
    }

    val isDeletable = canBeDeleted(image) && withDeletePermission

    val actions: List[Action] = if(tier == Internal) imageActions(id, isDeletable, withWritePermission) else Nil

    (data, links, actions)
  }

  def downloadLink(id: String) = Link("download", s"${services.apiBaseUri}/images/$id/download")

  def imageLinks(id: String, secureUrl: String, securePngUrl: Option[String], withWritePermission: Boolean, valid: Boolean) = {
    val cropLink = Link("crops", s"${services.cropperBaseUri}/crops/$id")
    val editLink = Link("edits", s"${services.metadataBaseUri}/metadata/$id")
    val optimisedLink = Link("optimised", makeImgopsUri(new URI(secureUrl)))
    val optimisedPngLink = securePngUrl match {
      case Some(secureUrl) => Some(Link("optimisedPng", makeImgopsUri(new URI(secureUrl))))
      case _ => None
    }
    val imageLink = Link("ui:image",  s"${services.kahunaBaseUri}/images/$id")
    val usageLink = Link("usages", s"${services.usageBaseUri}/usages/media/$id")
    val leasesLink = Link("leases", s"${services.leasesBaseUri}/leases/media/$id")
    val fileMetadataLink = Link("fileMetadata", s"${services.apiBaseUri}/images/$id/fileMetadata")

    val baseLinks = if (withWritePermission) {
      List(editLink, optimisedLink, imageLink, usageLink, leasesLink, fileMetadataLink, downloadLink(id))
    } else {
      List(optimisedLink, imageLink, usageLink, leasesLink, fileMetadataLink, downloadLink(id))
    }

    val baseLinksWithOptimised = optimisedPngLink match {
      case Some(link) => link :: baseLinks
      case None => baseLinks
    }

    if (valid) cropLink :: baseLinksWithOptimised else baseLinksWithOptimised
  }

  def imageActions(id: String, isDeletable: Boolean, withWritePermission: Boolean): List[Action] = {

    val imageUri = URI.create(s"${services.apiBaseUri}/images/$id")
    val reindexUri = URI.create(s"${services.apiBaseUri}/images/$id/reindex")
    val addCollectionUri = URI.create(s"${services.collectionsBaseUri}/images/$id")
    val addLeasesUri = URI.create(s"${services.leasesBaseUri}/leases")
    val replaceLeasesUri = URI.create(s"${services.leasesBaseUri}/leases/media/$id")
    val deleteLeasesUri = URI.create(s"${services.leasesBaseUri}/leases/media/$id")

    val deleteAction = Action("delete", imageUri, "DELETE")
    val reindexAction = Action("reindex", reindexUri, "POST")

    val addCollectionAction = Action("add-collection", addCollectionUri, "POST")

    val addLeasesAction = Action("add-lease", addLeasesUri, "POST")
    val replaceLeasesAction = Action("replace-leases", replaceLeasesUri, "POST")
    val deleteLeasesAction = Action("delete-leases", deleteLeasesUri, "DELETE")

    List(
      deleteAction        -> isDeletable,
      reindexAction       -> withWritePermission,
      addLeasesAction     -> withWritePermission,
      replaceLeasesAction -> withWritePermission,
      deleteLeasesAction  -> withWritePermission,
      addCollectionAction -> true
    )
    .filter{ case (action, active) => active }
    .map   { case (action, active) => action }
  }

  def addUsageCost(source: JsValue): Reads[JsObject] = {
    // We do the merge here as some records haven't had the user override applied
    // to the root level `usageRights`
    // TODO: Solve with reindex
    val usageRights = List(
      (source \ "usageRights").asOpt[JsObject],
      (source \ "userMetadata" \ "usageRights").asOpt[JsObject]
    ).flatten.foldLeft(Json.obj())(_ ++ _).as[UsageRights]

    val cost = Costing.getCost(usageRights)

    __.json.update(__.read[JsObject].map(_ ++ Json.obj("cost" -> cost.toString)))
  }

  def addSyndicationStatus(image: Image): Reads[JsObject] = {
    __.json.update(__.read[JsObject]).map(_ ++ Json.obj(
      "syndicationStatus" -> image.syndicationStatus
    ))
  }

  def addPersistedState(isPersisted: Boolean, persistenceReasons: List[String]): Reads[JsObject] =
    __.json.update(__.read[JsObject]).map(_ ++ Json.obj(
      "persisted" -> Json.obj(
        "value" -> isPersisted,
        "reasons" -> persistenceReasons)))

  def wrapUserMetadata(id: String): Reads[JsObject] =
    __.read[JsObject].map { root =>
      val edits = (root \ "userMetadata").asOpt[Edits].getOrElse(Edits.getEmpty)
      val editsJson = Json.toJson(editsEmbeddedEntity(id, edits))
      
      root ++ Json.obj("userMetadata" -> editsJson)
    }

  def addSecureSourceUrl(url: String): Reads[JsObject] =
    (__ \ "source").json.update(__.read[JsObject].map(_ ++ Json.obj("secureUrl" -> url)))

  def addSecureOptimisedPngUrl(url: String): Reads[JsObject] =
    (__ \ "optimisedPng").json.update(__.read[JsObject].map(_ ++ Json.obj("secureUrl" -> url)))

  def addSecureThumbUrl(url: String): Reads[JsObject] =
    (__ \ "thumbnail").json.update(__.read[JsObject].map (_ ++ Json.obj("secureUrl" -> url)))

  def addValidity(valid: Boolean): Reads[JsObject] =
    __.json.update(__.read[JsObject]).map(_ ++ Json.obj("valid" -> valid))

  def addInvalidReasons(reasons: Map[String, String]): Reads[JsObject] =
    __.json.update(__.read[JsObject]).map(_ ++ Json.obj("invalidReasons" -> Json.toJson(reasons)))

  def makeImgopsUri(uri: URI): String =
    services.imgopsBaseUri + List(uri.getPath, uri.getRawQuery).mkString("?") + "{&w,h,q}"

  def makeOptimisedPngImageopsUri(uri: URI): String = {
    services.imgopsBaseUri + List(uri.getPath, uri.getRawQuery).mkString("?") + "{&w, h, q}"
  }


  import play.api.libs.json.JodaWrites._

  def imageResponseWrites(id: String, expandFileMetaData: Boolean): Writes[Image] = (
    (__ \ "id").write[String] ~
    (__ \ "uploadTime").write[DateTime] ~
    (__ \ "uploadedBy").write[String] ~
    (__ \ "lastModified").writeNullable[DateTime] ~
    (__ \ "identifiers").write[Map[String,String]] ~
    (__ \ "uploadInfo").write[UploadInfo] ~
    (__ \ "source").write[Asset] ~
    (__ \ "thumbnail").writeNullable[Asset] ~
    (__ \ "optimisedPng").writeNullable[Asset] ~
    (__ \ "fileMetadata").write[FileMetadataEntity]
      .contramap(fileMetadataEntity(id, expandFileMetaData, _: FileMetadata)) ~
    (__ \ "userMetadata").writeNullable[Edits] ~
    (__ \ "metadata").write[ImageMetadata] ~
    (__ \ "originalMetadata").write[ImageMetadata] ~
    (__ \ "usageRights").write[UsageRights] ~
    (__ \ "originalUsageRights").write[UsageRights] ~
    (__ \ "exports").write[List[Export]]
      .contramap((crops: List[Crop]) => crops.map(Export.fromCrop(_:Crop))) ~
    (__ \ "usages").write[UsagesEntity]
      .contramap(usagesEntity(id, _: List[Usage])) ~
    (__ \ "leases").write[MediaLeasesEntity]
        .contramap(leasesEntity(id, _: LeasesByMedia)) ~
    (__ \ "collections").write[List[EmbeddedEntity[CollectionResponse]]]
      .contramap((collections: List[Collection]) => collections.map(c => collectionsEntity(id, c))) ~
    (__ \ "syndicationRights").write[Option[SyndicationRights]] ~
    (__ \ "usermetaDataLastModified").writeNullable[DateTime]

    )(unlift(Image.unapply))

  def fileMetaDataUri(id: String) = URI.create(s"${services.apiBaseUri}/images/$id/fileMetadata")

  def usagesUri(id: String) = URI.create(s"${services.usageBaseUri}/usages/media/$id")
  def usageUri(id: String) = {
    URI.create(s"${services.usageBaseUri}/usages/${UriEncoding.encodePathSegment(id, "UTF-8")}")
  }
  def leasesUri(id: String) = URI.create(s"${services.leasesBaseUri}/leases/media/$id")

  def usageEntity(usage: Usage) = EmbeddedEntity[Usage](usageUri(usage.id), Some(usage))
  def usagesEntity(id: String, usages: List[Usage]) =
    EmbeddedEntity[List[UsageEntity]](usagesUri(id), Some(usages.map(usageEntity)))

  def leasesEntity(id: String, leaseByMedia: LeasesByMedia) =
    EmbeddedEntity[LeasesByMedia](leasesUri(id), Some(leaseByMedia))

  def collectionsEntity(id: String, c: Collection): EmbeddedEntity[CollectionResponse] =
      collectionEntity(services.collectionsBaseUri, id, c)

  def collectionEntity(rootUri: String, imageId: String, c: Collection) = {
    // TODO: Currently the GET for this URI does nothing
    val uri = URI.create(s"$rootUri/images/$imageId/${CollectionsManager.pathToUri(c.path)}")
    val response = CollectionResponse.build(c)
    EmbeddedEntity(uri, Some(response), actions = List(
      Action("remove", uri, "DELETE")
    ))
  }

  def fileMetadataEntity(id: String, expandFileMetaData: Boolean, fileMetadata: FileMetadata) = {
    val displayableMetadata = if(expandFileMetaData) Some(fileMetadata) else None

    EmbeddedEntity[FileMetadata](fileMetaDataUri(id), displayableMetadata)
  }
}

// We're using this to slightly hydrate the json response
case class CollectionResponse private (path: List[String], pathId: String, description: String, cssColour: Option[String], actionData: ActionData)
object CollectionResponse {
  implicit def writes: Writes[CollectionResponse] = Json.writes[CollectionResponse]

  def build(c: Collection) =
    CollectionResponse(c.path, c.pathId, c.description, CollectionsManager.getCssColour(c.path), c.actionData)
}
