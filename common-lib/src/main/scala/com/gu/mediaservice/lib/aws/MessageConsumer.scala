package com.gu.mediaservice.lib.aws

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

import _root_.play.api.libs.functional.syntax._
import _root_.play.api.libs.json._
import akka.actor.ActorSystem
import com.amazonaws.services.cloudwatch.model.Dimension
import com.amazonaws.services.sqs.model.{DeleteMessageRequest, ReceiveMessageRequest, Message => SQSMessage}
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import com.gu.mediaservice.lib.config.CommonConfig
import com.gu.mediaservice.lib.json.PlayJsonHelpers._
import com.gu.mediaservice.lib.metrics.Metric
import com.gu.mediaservice.model.usage.UsageNotice
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.Logger
import scalaz.syntax.id._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

abstract class MessageConsumer(queueUrl: String, awsEndpoint: String, config: CommonConfig, metric: Metric[Long]) {
  val actorSystem = ActorSystem("MessageConsumer")

  private implicit val ctx: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool)

  def startSchedule(): Unit =
    actorSystem.scheduler.scheduleOnce(0.seconds)(processMessages())

  lazy val client: AmazonSQS = config.withAWSCredentials(AmazonSQSClientBuilder.standard()).build()

  def chooseProcessor(message: UpdateMessage): Option[UpdateMessage => Future[Any]]

  val timeMessageLastProcessed = new AtomicReference[DateTime](DateTime.now)

  @tailrec
  final def processMessages(): Unit = {
    // Pull 1 message at a time to avoid starvation
    // Wait for maximum duration (20s) as per doc recommendation:
    // http://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-long-polling.html

    implicit val yourJodaDateReads = JodaReads.DefaultJodaDateTimeReads
    implicit val unr = Json.reads[UsageNotice]
    implicit val umr = Json.reads[UpdateMessage]

    for (msg <- getMessages(waitTime = 20, maxMessages = 1)) {
      Logger.debug("Processing message: " + msg)
      val future = for {
        snsMessage <- Future(extractSNSMessage(msg) getOrElse {
          val errorMessage = "Invalid message structure (not via SNS?)"
          Logger.error(errorMessage)
          sys.error(errorMessage)
        })
        message = snsMessage.body.as[UpdateMessage] // TODO validation
        processor = chooseProcessor(message)
        _ <- processor.fold(
          sys.error(s"Unrecognised message subject ${message.subject}"))(
            _.apply(message))
        _ = recordMessageCount(message)
        _ = timeMessageLastProcessed.lazySet(DateTime.now)
      } yield ()
      future |> deleteOnSuccess(msg)
    }

    processMessages()
  }

  private def recordMessageCount(message: UpdateMessage) = {
    metric.runRecordOne(1L, List(new Dimension().withName("subject").withValue(message.subject)))
  }

  private def deleteOnSuccess(msg: SQSMessage)(f: Future[Any]): Unit =
    f.foreach { _ => deleteMessage(msg) }

  private def getMessages(waitTime: Int, maxMessages: Int): Seq[SQSMessage] = {
    Logger.info("Getting messages from queue URL: " + queueUrl)
    client.receiveMessage(
      new ReceiveMessageRequest(queueUrl)
        .withWaitTimeSeconds(waitTime)
        .withMaxNumberOfMessages(maxMessages)
    ).getMessages.asScala.toList
  }

  private def extractSNSMessage(sqsMessage: SQSMessage): Option[SNSMessage] = {
    Json.fromJson[SNSMessage](Json.parse(sqsMessage.getBody)) <| logParseErrors |> (_.asOpt)
  }
  private def deleteMessage(message: SQSMessage): Unit =
    client.deleteMessage(new DeleteMessageRequest(queueUrl, message.getReceiptHandle))
}

// TODO: improve and use this (for logging especially) else where.
case class EsResponse(message: String)
case class SNSBodyParseError(message: String) extends Exception

case class SNSMessage(
  messageType: String,
  messageId: String,
  topicArn: String,
  subject: Option[String],
  timestamp: DateTime,
  body: JsValue
)

object SNSMessage {
  private def parseTimestamp(timestamp: String): DateTime =
    ISODateTimeFormat.dateTime.withZoneUTC.parseDateTime(timestamp)

  implicit def snsMessageReads: Reads[SNSMessage] =
    (
      (__ \ "Type").read[String] ~
        (__ \ "MessageId").read[String] ~
        (__ \ "TopicArn").read[String] ~
        (__ \ "Subject").readNullable[String] ~
        (__ \ "Timestamp").read[String].map(parseTimestamp) ~
        (__ \ "Message").read[String].map(Json.parse)
      ) (SNSMessage(_, _, _, _, _, _))
}
