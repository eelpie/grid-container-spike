package lib

import com.amazonaws.services.cloudwatch.model.Dimension
import com.gu.mediaservice.lib.metrics.Metric
import scalaz.concurrent.Task

import scala.concurrent.Future

class NullThrallMetrics extends ThrallMetrics {

  val indexedImages = new NullMetric[Long]

  val deletedImages: Metric[Long] = new NullMetric[Long]

  val failedDeletedImages: Metric[Long] = new NullMetric[Long]

  val failedMetadataUpdates: Metric[Long] = new NullMetric[Long]

  val failedCollectionsUpdates: Metric[Long] = new NullMetric[Long]

  val failedExportsUpdates: Metric[Long] = new NullMetric[Long]

  val failedUsagesUpdates: Metric[Long] = new NullMetric[Long]

  val failedSyndicationRightsUpdates: Metric[Long] = new NullMetric[Long]

  val snsMessage: Metric[Long] = new NullMetric[Long]


  class NullMetric[A] extends Metric[A] {
    override def recordOne(a: A, dimensions: List[Dimension]): Task[Unit] = Task(Future.successful{1})

    override def recordMany(as: Seq[A], dimensions: List[Dimension]): Task[Unit] = Task(Future.successful{1})

    override def runRecordOne(a: A, dimensions: List[Dimension]): Unit = Unit

    override def runRecordMany(as: Seq[A], dimensions: List[Dimension]): Unit = Unit
  }

}