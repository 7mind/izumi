package com.github.pshirshov.izumi.logstage.api

trait AbstractLogger {
  def log(entry: Log.Entry): Unit

  def debug(message: Log.Message): Unit
  def info(message: Log.Message): Unit
  def warn(message: Log.Message): Unit
  def error(message: Log.Message): Unit
  def crit(message: Log.Message): Unit
}
