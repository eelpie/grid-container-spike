package com.gu.mediaservice.lib.config

import java.io.File
import java.util.UUID

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder
import play.api.Configuration

import scala.io.Source._

trait CommonConfig {
  def appName: String
  def configuration: Configuration

  val domainRoot: String = configuration.get[String]("domain.root")

  // TODO want to drive out this Guardian native config file
  lazy val properties: Map[String, String] = Properties.fromPath(s"/etc/gu/$appName.properties")

  final val awsEndpoint = "ec2.eu-west-1.amazonaws.com"

  final val elasticsearchStack = "media-service"

  final val elasticsearchApp = "elasticsearch"
  final val elasticsearch6App = "elasticsearch6"

  final val stackName = "media-service"

  final val sessionId = UUID.randomUUID().toString

  val awsAccessKey = configuration.get[String]("ec2.accessKey")
  val awsSecretKey =  configuration.get[String]("ec2.secretKey")
  lazy val awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsAccessKey, awsSecretKey))

  lazy val awsRegion = properties.getOrElse("aws.region", "eu-west-1")

  def withAWSCredentials[T, S <: AwsClientBuilder[S, T]](builder: AwsClientBuilder[S, T]): S = builder
    .withRegion(awsRegion)
    .withCredentials(awsCredentials)

  final val stage: String = stageFromFile getOrElse "DEV"

  val isProd: Boolean = stage == "PROD"
  val isDev: Boolean = stage == "DEV"

  final val thrallKinesisStream = s"$stackName-thrall-$stage"

  final def apply(key: String): String =
    string(key)

  final def string(key: String): String =
    configuration.getOptional[String](key) getOrElse missing(key, "string")

  final def stringOpt(key: String): Option[String] = configuration.getOptional[String](key)

  final def int(key: String): Int =
    configuration.getOptional[Int](key) getOrElse missing(key, "integer")

  private def missing(key: String, type_ : String): Nothing =
    sys.error(s"Required $type_ configuration property missing: $key")

  private def stageFromFile: Option[String] = {
    val file = new File("/etc/gu/stage")
    if (file.exists) Some(fromFile(file).mkString.trim) else None
  }

}
