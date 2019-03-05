package lib

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.gu.mediaservice.lib.auth.Tier
import com.gu.mediaservice.lib.metrics.CloudWatchMetrics

class CloudWatchMediaApiMetrics(namespace: String, withAWSCredentials: AmazonCloudWatchClientBuilder => AmazonCloudWatchClientBuilder)
  extends CloudWatchMetrics(namespace + "/MediaApi", withAWSCredentials) with MediaApiMetrics {

  override val searchQueryDuration: TimeMetric = new TimeMetric("ElasticSearch")

  override def originalImageDownloadMetricForTier(tier: Tier): CountMetric = new CountMetric(tier.toString)

}