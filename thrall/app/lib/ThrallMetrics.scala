package lib

import com.gu.mediaservice.lib.metrics.Metric

trait ThrallMetrics {

  def indexedImages: Metric[Long]

  def deletedImages: Metric[Long] 

  def failedDeletedImages: Metric[Long]

  def failedMetadataUpdates: Metric[Long]

  def failedCollectionsUpdates: Metric[Long] 

  def failedExportsUpdates: Metric[Long]

  def failedUsagesUpdates: Metric[Long]
  def failedSyndicationRightsUpdates: Metric[Long] 

  def failedQueryUpdates: Metric[Long]

  def failedDeletedAllUsages: Metric[Long]

  def processingLatency: Metric[Long]  // TODO Time aspect is lost in the Metrics interace

  def snsMessage: Metric[Long] 

}
