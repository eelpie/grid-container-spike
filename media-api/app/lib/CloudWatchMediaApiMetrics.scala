package lib

import com.gu.mediaservice.lib.auth.Tier
import com.gu.mediaservice.lib.metrics.{CloudWatchMetrics, Metric}

class CloudWatchMediaApiMetrics(config: MediaApiConfig) extends CloudWatchMetrics(s"${config.stage}/MediaApi", config)
with MediaApiMetrics {

  override val searchQueryDuration: TimeMetric = new TimeMetric("ElasticSearch")

  override def originalImageDownloadMetricForTier(tier: Tier): CountMetric = new CountMetric(tier.toString)

}