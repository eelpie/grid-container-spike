package lib

import com.gu.mediaservice.lib.aws.{MessageConsumer, UpdateMessage}
import com.gu.mediaservice.lib.metrics.Metric
import lib.kinesis.MessageProcessor
import org.joda.time.DateTime

import scala.concurrent.{ExecutionContext, Future}

class ThrallMessageConsumer(
  config: ThrallConfig,
  es: ElasticSearchVersion,
  thrallMetrics: ThrallMetrics,
  store: ThrallStore,
  metadataNotifications: DynamoNotifications,
  syndicationRightsOps: SyndicationRightsOps,
  messageCountMetric: Metric[Long]
)(implicit ec: ExecutionContext) extends MessageConsumer (
  config.queueUrl,
  config.awsEndpoint,
  config,
  messageCountMetric
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