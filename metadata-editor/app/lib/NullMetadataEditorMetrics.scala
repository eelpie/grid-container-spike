package lib
import com.gu.mediaservice.lib.metrics.NullMetric

class NullMetadataEditorMetrics extends MetadataEditorMetrics {
  override def snsMessage = new NullMetric[Long]()
}
