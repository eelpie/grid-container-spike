package lib

import java.io.File

import com.gu.mediaservice.lib.config.{CommonConfig, Services}
import play.api.Configuration

class ImageLoaderConfig(override val configuration: Configuration) extends CommonConfig {

  final override lazy val appName = "image-loader"

  val topicArn = configuration.getOptional[String]("sns.topic.arn")

  val imageBucket: String = configuration.get[String]("s3.image.bucket")

  val thumbnailBucket: String = configuration.get[String]("s3.thumb.bucket")

  val tempDir: File = new File(configuration.getOptional[String]("upload.tmp.dir").
    getOrElse("/tmp")) // TODO Api call rather than /tmp

  val thumbWidth: Int = 256
  val thumbQuality: Double = 85d // out of 100

  val supportedMimeTypes = List("image/jpeg", "image/png")

}
