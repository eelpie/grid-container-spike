package lib

import com.gu.mediaservice.lib.aws.{MessageSender, MessageSenderVersion}

class Notifications(publishers: Seq[MessageSenderVersion]) extends MessageSender(publishers)