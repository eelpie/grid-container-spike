package lib

import com.gu.mediaservice.lib.metrics.CloudWatchMetrics

class CloudWatchMetadataEditorMetrics(config: EditsConfig) extends CloudWatchMetrics(s"${config.stage}/MetadataEditor", config)
  with MetadataEditorMetrics {

  val snsMessage = new CountMetric("SNSMessage")

}
