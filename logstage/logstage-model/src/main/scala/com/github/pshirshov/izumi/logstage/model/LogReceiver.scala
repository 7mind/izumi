package com.github.pshirshov.izumi.logstage.model

trait LogReceiver {
  def log(context: Log.Context, message: Log.Message): Unit
  def level: Log.Level

}
