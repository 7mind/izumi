package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.logstage.model.Log

trait LogConfigService { // for now we may implement that even in code

  def config(e: Log.Entry): Seq[LogMapping]
}
