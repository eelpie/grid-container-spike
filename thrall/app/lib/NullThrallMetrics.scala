package lib

import com.gu.mediaservice.lib.metrics.NullMetric

class NullThrallMetrics extends ThrallMetrics {
  val indexedImages = new NullMetric[Long]
  val deletedImages = new NullMetric[Long]
  val failedDeletedImages = new NullMetric[Long]
  val failedMetadataUpdates = new NullMetric[Long]
  val failedCollectionsUpdates = new NullMetric[Long]
  val failedExportsUpdates = new NullMetric[Long]
  val failedUsagesUpdates = new NullMetric[Long]
  val failedSyndicationRightsUpdates = new NullMetric[Long]
  val snsMessage = new NullMetric[Long]
}