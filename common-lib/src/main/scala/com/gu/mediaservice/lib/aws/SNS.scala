package com.gu.mediaservice.lib.aws

import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.sns.{AmazonSNS, AmazonSNSClientBuilder}
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.model.usage.UsageNotice
import play.api.Logger
import play.api.libs.json.{JodaWrites, Json}

class SNS(config: CommonConfig, topicArn: String) extends MessageSenderVersion {
  lazy val client: AmazonSNS = config.withAWSCredentials(AmazonSNSClientBuilder.standard()).build()

  def publish(updateMessage: UpdateMessage) {
    implicit val jodaDateTimeWrites = JodaWrites.JodaDateTimeWrites
    implicit val unw = Json.writes[UsageNotice]
    implicit val umw = Json.writes[UpdateMessage]
    val result = client.publish(new PublishRequest(topicArn, Json.stringify(Json.toJson(updateMessage)), updateMessage.subject))
    Logger.info(s"Published message: $result")
  }

}
