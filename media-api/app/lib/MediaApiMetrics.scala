package lib

import com.amazonaws.services.cloudwatch.model.Dimension
import com.gu.mediaservice.lib.auth.{ApiKey, Syndication, Tier}
import com.gu.mediaservice.lib.metrics.Metric

trait MediaApiMetrics {

  def searchQueryDuration: Metric[Long] // TODO Is actually a duration

  def originalImageDownloadMetricForTier(tier: Tier):  Metric[Long]

  def incrementOriginalImageDownload(apiKey: ApiKey) = {
    // CW Metrics have a maximum of 10 dimensions per metric.
    // Create a separate dimension per syndication partner and group other Tier types together.
    val dimensionValue: String = apiKey.tier match {
      case Syndication => apiKey.name
      case _ => apiKey.tier.toString
    }

    val dimension = new Dimension().withName("OriginalImageDownload").withValue(dimensionValue)

    originalImageDownloadMetricForTier(apiKey.tier).runRecordOne(1, List(dimension))
  }

  def searchTypeDimension(value: String): Dimension =
    new Dimension().withName("SearchType").withValue(value)

}
