package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.LoggerHook

class LoggerHookDefaultImpl extends LoggerHook {
  override def log(message: => String): Unit = {}
}

