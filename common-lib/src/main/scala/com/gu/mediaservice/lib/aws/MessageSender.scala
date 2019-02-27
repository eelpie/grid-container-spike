package com.gu.mediaservice.lib.aws

import com.gu.mediaservice.model._
import com.gu.mediaservice.model.usage.UsageNotice
import org.joda.time.DateTime
import play.api.Logger

class MessageSender(publishers: Seq[MessageSenderVersion]) {
  def publish(updateMessage: UpdateMessage): Unit = {
    publishers.map { publisher =>
      Logger.info("Publishing message to publisher " + publisher.toString + ": " + updateMessage)
      publisher.publish(updateMessage)
    }
  }
}

case class UpdateMessage(subject: String,
                         image: Option[Image] = None,
                         id: Option[String] = None,
                         usageNotice: Option[UsageNotice] = None,
                         edits: Option[Edits] = None,
                         lastModified: Option[DateTime] = None,
                         collections: Option[Seq[Collection]] = None,
                         leaseId: Option[String] = None,
                         crops: Option[Seq[Crop]] = None,
                         mediaLease: Option[MediaLease] = None,
                         leases: Option[Seq[MediaLease]] = None,
                         syndicationRights: Option[SyndicationRights] = None
                        )