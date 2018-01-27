package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.logstage.model.Log


trait LogFilter {
  def accept(e: Log.Entry): Boolean
}


