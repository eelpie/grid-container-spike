package com.gu.mediaservice.lib.aws

trait MessageSenderVersion {

  def publish(updateMessage: UpdateMessage): Unit

}
