package com.github.pshirshov.izumi.logstage.model.logger

import com.github.pshirshov.izumi.logstage.model.Log

trait LogRouter {
  def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean
  def log(entry: Log.Entry): Unit
}

