package lib

import com.gu.mediaservice.lib.metrics.CloudWatchMetrics

class CloudWatchUsageMetrics(config: UsageConfig) extends CloudWatchMetrics(s"${config.stage}/Usage", config)
  with UsageMetrics {

  def incrementUpdated = updates.increment().run
  def incrementErrors = errors.increment().run

  private val updates = new CountMetric("UsageUpdates")
  private val errors = new CountMetric("UsageUpdateErrors")

}