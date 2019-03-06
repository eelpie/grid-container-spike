package lib

import com.gu.mediaservice.lib.aws.{MessageSender, MessageSenderVersion, UpdateMessage}
import com.gu.mediaservice.lib.formatting._
import com.gu.mediaservice.model.{LeasesByMedia, MediaLease}
import org.joda.time.DateTime
import play.api.libs.json._

case class LeaseNotice(mediaId: String, leaseByMedia: JsValue) {
  def toJson = Json.obj(
    "id" -> mediaId,
    "data" -> leaseByMedia,
    "lastModified" -> printDateTime(DateTime.now())
  )
}

object LeaseNotice {
  import JodaWrites._

  implicit val writer = new Writes[LeasesByMedia] {
    def writes(leaseByMedia: LeasesByMedia) = {
      LeasesByMedia.toJson(
        Json.toJson(leaseByMedia.leases),
        Json.toJson(leaseByMedia.lastModified.map(lm => Json.toJson(lm)))
      )
    }
  }
  def apply(mediaLease: MediaLease): LeaseNotice = LeaseNotice(
    mediaLease.mediaId,
    Json.toJson(LeasesByMedia.build(List(mediaLease)))
  )
}

class LeaseNotifier(publishers: Seq[MessageSenderVersion], store: LeaseStore) extends MessageSender(publishers) {
  private def build(mediaId: String, leases: List[MediaLease] ): LeaseNotice = {
    LeaseNotice(mediaId, Json.toJson(LeasesByMedia.build(leases)))
  }

  def sendReindexLeases(mediaId: String) = {
    val leases = store.getForMedia(mediaId)
    publish(UpdateMessage(subject = "replace-image-leases", leases = Some(leases), id = Some(mediaId)))
  }

  def sendAddLease(mediaLease: MediaLease) = {
    val updateMessage = UpdateMessage(subject = "add-image-lease", mediaLease = Some(mediaLease), id = Some(mediaLease.mediaId), lastModified = Some(DateTime.now()))
    publish(updateMessage)
  }

  def sendRemoveLease(mediaId: String, leaseId: String) = {
    val updateMessage = UpdateMessage(subject = "remove-image-lease", id = Some(mediaId),
      leaseId = Some(leaseId), lastModified = Some(DateTime.now())
    )
    publish(updateMessage)
  }
}
