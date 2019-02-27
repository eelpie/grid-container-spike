package com.gu.mediaservice.lib.play

import com.gu.mediaservice.lib.logging.LogConfig
import com.gu.mediaservice.lib.logging.LogConfig.rootLogger
import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader}

abstract class GridAppLoader(loadFn: Context => GridComponents) extends ApplicationLoader {
  final override def load(context: Context): Application = {
    LogConfig.initPlayLogging(context)

    val gridApp = loadFn(context)

    gridApp.config.kinesisLoggingConfiguration.fold {
      rootLogger.info("Kinesis log appender is not configured")
    } { kinesisLoggingConfiguration =>
      LogConfig.initKinesisLogging(gridApp.config, kinesisLoggingConfiguration)
    }

    gridApp.application
  }
}
