package com.github.pshirshov.izumi.logstage.api.logger

trait LogConfigService { // for now we may implement that even in code

  def config(e: Log.Entry): Seq[LogMapping]
}
