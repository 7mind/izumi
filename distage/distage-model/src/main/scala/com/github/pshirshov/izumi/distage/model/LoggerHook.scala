package com.github.pshirshov.izumi.distage.model

trait LoggerHook {
  def log(message: => String): Unit
}
