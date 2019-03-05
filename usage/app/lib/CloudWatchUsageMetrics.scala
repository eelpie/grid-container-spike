package lib

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.gu.mediaservice.lib.metrics.CloudWatchMetrics

class CloudWatchUsageMetrics(namespace: String, withAWSCredentials: AmazonCloudWatchClientBuilder => AmazonCloudWatchClientBuilder)
  extends CloudWatchMetrics(namespace + "/Usage", withAWSCredentials) with UsageMetrics {

  def incrementUpdated = updates.increment().run
  def incrementErrors = errors.increment().run

  private val updates = new CountMetric("UsageUpdates")
  private val errors = new CountMetric("UsageUpdateErrors")

}