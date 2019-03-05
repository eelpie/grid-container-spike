package lib

import com.gu.mediaservice.lib.auth.Tier
import com.gu.mediaservice.lib.metrics.NullMetric
import org.joda.time.Duration

class NullMediaApiMetrics extends MediaApiMetrics {

  def searchQueryDuration = new NullMetric[Duration]

  def originalImageDownloadMetricForTier(tier: Tier) = new NullMetric[Long]

}
