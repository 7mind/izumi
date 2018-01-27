package com.github.pshirshov.izumi.logstage.api.config

import com.github.pshirshov.izumi.logstage.model.Log

trait LogConfigService {
  def config(e: Log.Entry): Seq[LogMapping]
}

