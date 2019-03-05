package lib

import com.gu.mediaservice.lib.metrics.Metric

trait MetadataEditorMetrics {

  def snsMessage: Metric[Long]

}
