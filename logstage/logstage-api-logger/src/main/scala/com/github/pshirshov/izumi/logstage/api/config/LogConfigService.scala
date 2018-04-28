package com.github.pshirshov.izumi.logstage.api.config

import com.github.pshirshov.izumi.logstage.api.Log


trait LogConfigService {
  def threshold(loggerId: Log.LoggerId): Log.Level

  def config(entry: Log.Entry): LogEntryConfig
}


