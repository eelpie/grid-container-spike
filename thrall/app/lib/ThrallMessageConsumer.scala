package lib

import com.gu.mediaservice.lib.aws.{MessageConsumer, UpdateMessage}
import lib.kinesis.MessageProcessor
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}

class ThrallMessageConsumer(
  config: ThrallConfig,
  es: ElasticSearchVersion,
  store: ThrallStore,
  metadataNotifications: DynamoNotifications,
  syndicationRightsOps: SyndicationRightsOps,
  thrallMetrics: ThrallMetrics
)(implicit ec: ExecutionContext) extends MessageConsumer (
  config.queueUrl,
  config.awsEndpoint,
  config,
  thrallMetrics.snsMessage
) with MessageConsumerVersion {

  val messageProcessor = new MessageProcessor(es, store, metadataNotifications, syndicationRightsOps)

  override def chooseProcessor(message: UpdateMessage): Option[UpdateMessage => Future[Any]] = {
    messageProcessor.chooseProcessor(message)
  }

  override def isStopped: Boolean = {
    actorSystem.whenTerminated.isCompleted
  }

  override def lastProcessed: DateTime = timeMessageLastProcessed.get

}