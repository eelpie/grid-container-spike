package lib

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.gu.mediaservice.lib.metrics.CloudWatchMetrics

class CloudWatchMetadataEditorMetrics(namespace: String, withAWSCredentials: AmazonCloudWatchClientBuilder => AmazonCloudWatchClientBuilder)
  extends CloudWatchMetrics(namespace + "/MetadataEditor", withAWSCredentials) with MetadataEditorMetrics {

  val snsMessage = new CountMetric("SNSMessage")

}
