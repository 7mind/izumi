package com.github.pshirshov.izumi.logstage.model

import Log.Message

trait AbstractLogger {
  def debug(message: Message): Unit
}
