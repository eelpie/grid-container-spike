package com.gu.mediaservice.lib.metrics

import com.amazonaws.services.cloudwatch.model.Dimension
import scalaz.concurrent.Task

import scala.concurrent.Future

class NullMetric[A] extends Metric[A] {
  override def recordOne(a: A, dimensions: List[Dimension]): Task[Unit] = Task(Future.successful{1})
  override def recordMany(as: Seq[A], dimensions: List[Dimension]): Task[Unit] = Task(Future.successful{1})
  override def runRecordOne(a: A, dimensions: List[Dimension]): Unit = Unit
  override def runRecordMany(as: Seq[A], dimensions: List[Dimension]): Unit = Unit
}