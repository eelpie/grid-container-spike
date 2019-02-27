package lib

import java.io.File

import com.gu.mediaservice.lib.config.CommonConfig
import play.api.Configuration

class CropperConfig(override val configuration: Configuration) extends CommonConfig {

  final override lazy val appName = "cropper"

  val imgPublishingBucket: String = configuration.get[String]("publishing.image.bucket")
  val imgPublishingSecureHost: String = configuration.get[String]("publishing.image.secure.host")

  val topicArn: Option[String] = configuration.getOptional[String]("sns.topic.arn")

  val tempDir: File = new File(configuration.getOptional[String]("crop.output.tmp.dir").
    getOrElse("/tmp")) // TODO Api call rather than /tmp

  val landscapeCropSizingWidths = List(2000, 1000, 500, 140)
  val portraitCropSizingHeights = List(2000, 1000, 500)

}
