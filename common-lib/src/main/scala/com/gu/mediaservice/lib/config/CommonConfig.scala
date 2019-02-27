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

  val pandaSystem: String = configuration.get[String]("panda.system")
  val pandaSettingsBucket: String = configuration.get[String]("panda.bucket")
  val pandaUserDomain: String = configuration.get[String]("panda.userDomain")

  val authKeyStoreBucket: String = configuration.get[String]("auth.keystore.bucket")

  final val awsEndpoint = "ec2.eu-west-1.amazonaws.com"

  final val elasticsearchStack = "media-service"

  final val elasticsearchApp = "elasticsearch"
  final val elasticsearch6App = "elasticsearch6"

  final val stackName = "media-service"

  final val sessionId = UUID.randomUUID().toString

  val awsAccessKey = configuration.get[String]("ec2.accessKey")
  val awsSecretKey =  configuration.get[String]("ec2.secretKey")
  lazy val awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(awsAccessKey, awsSecretKey))

  lazy val awsRegion = configuration.getOptional[String]("aws.region").getOrElse("eu-west-1")

  def withAWSCredentials[T, S <: AwsClientBuilder[S, T]](builder: AwsClientBuilder[S, T]): S = builder
    .withRegion(awsRegion)
    .withCredentials(awsCredentials)

  final val stage: String = stageFromFile getOrElse "DEV"

  val isProd: Boolean = stage == "PROD"
  val isDev: Boolean = stage == "DEV"

  final val thrallKinesisStream: Option[String] = None // Some(s"$stackName-thrall-$stage")

  val loggerKinesisStream = configuration.get[String]("logger.kinesis.stream")
  val loggerKinesisRegion = configuration.get[String]("logger.kinesis.region")
  val loggerKinesisRoleArn = configuration.get[String]("logger.kinesis.roleArn")

  val permissionsBucket = configuration.getOptional[String]("permissions.bucket")
  val permissionsStage = configuration.getOptional[String]("permissions.stage") // TODO Do not want

  // TODO purge - move to Play config
  private def stageFromFile: Option[String] = {
    val file = new File("/etc/gu/stage")
    if (file.exists) Some(fromFile(file).mkString.trim) else None
  }

}
