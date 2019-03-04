package com.gu.mediaservice.lib.metrics

import com.amazonaws.services.cloudwatch.model.Dimension
import scalaz.concurrent.Task

trait Metric[A] {

  def recordOne(a: A, dimensions: List[Dimension] = Nil): Task[Unit]

  def recordMany(as: Seq[A], dimensions: List[Dimension] = Nil): Task[Unit]

  def runRecordOne(a: A, dimensions: List[Dimension] = Nil): Unit

  def runRecordMany(as: Seq[A], dimensions: List[Dimension] = Nil): Unit
}
