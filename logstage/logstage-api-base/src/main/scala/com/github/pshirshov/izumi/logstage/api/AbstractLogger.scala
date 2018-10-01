package com.github.pshirshov.izumi.logstage.api


trait AbstractLogger {
  def debug(message: String): Unit
  def info(message: String): Unit
  def warn(message: String): Unit
  def error(message: String): Unit
  def crit(message: String): Unit
  def entry(logLevel: Log.Level)(message: String): Log.Entry
}
