package lib

import com.amazonaws.services.cloudwatch.model.Dimension
import com.gu.mediaservice.lib.auth.{ApiKey, Syndication, Tier}
import com.gu.mediaservice.lib.metrics.CloudWatchMetrics

class MediaApiMetrics(config: MediaApiConfig) extends CloudWatchMetrics(s"${config.stage}/MediaApi", config) {

  val searchQueryDuration: TimeMetric = new TimeMetric("ElasticSearch")

  def metric(tier: Tier): CountMetric = new CountMetric(tier.toString)

  def incrementOriginalImageDownload(apiKey: ApiKey) = {
    // CW Metrics have a maximum of 10 dimensions per metric.
    // Create a separate dimension per syndication partner and group other Tier types together.
    val dimensionValue: String = apiKey.tier match {
      case Syndication => apiKey.name
      case _ => apiKey.tier.toString
    }

    val dimension = new Dimension().withName("OriginalImageDownload").withValue(dimensionValue)

    val tier: Tier = apiKey.tier
    metric(tier).increment(List(dimension)).run
  }

  def searchTypeDimension(value: String): Dimension =
    new Dimension().withName("SearchType").withValue(value)

}
