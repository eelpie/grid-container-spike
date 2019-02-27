package lib

import com.gu.mediaservice.lib.aws.{MessageConsumer, UpdateMessage}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MetadataMessageConsumer(config: EditsConfig, metadataEditorMetrics: MetadataEditorMetrics, store: EditsStore) extends MessageConsumer(
  config.queueUrl, config.awsEndpoint, config, metadataEditorMetrics.processingLatency) {

  override def chooseProcessor(message: UpdateMessage): Option[UpdateMessage => Future[Any]] =
    PartialFunction.condOpt(message.subject) {
      case "image-deleted" => processDeletedImage
    }

  def processDeletedImage(message: UpdateMessage) = Future {
    message.id.map { id =>
      store.deleteItem(id)
    }
  }
}
