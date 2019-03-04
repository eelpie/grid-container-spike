package lib

import com.gu.mediaservice.lib.metrics.{CloudWatchMetrics, Metric}

class CloudWatchThrallMetrics(config: ThrallConfig) extends CloudWatchMetrics(s"${config.stage}/Thrall", config)
  with ThrallMetrics {

  val indexedImages = new CountMetric("IndexedImages")

  val deletedImages: Metric[Long] = new CountMetric("DeletedImages")

  val failedDeletedImages: Metric[Long] = new CountMetric("FailedDeletedImages")

  val failedMetadataUpdates: Metric[Long] = new CountMetric("FailedMetadataUpdates")

  val failedCollectionsUpdates: Metric[Long] = new CountMetric("FailedCollectionsUpdates")

  val failedExportsUpdates: Metric[Long] = new CountMetric("FailedExportsUpdates")

  val failedUsagesUpdates: Metric[Long] = new CountMetric("FailedUsagesUpdates")

  val failedSyndicationRightsUpdates: Metric[Long] = new CountMetric("FailedSyndicationRightsUpdates")

  val snsMessage: Metric[Long] = new CountMetric("SNSMessage")

}
