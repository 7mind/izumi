package com.github.pshirshov.izumi.logstage.api.logger

import com.github.pshirshov.izumi.logstage.model.Log.{Entry, Message}
import com.github.pshirshov.izumi.logstage.model.{Log, LogReceiver}


trait RoutingLogReceiver extends LogReceiver {

  protected def logConfigService: LogConfigService

  def log(context: Log.Context, message: Message): Unit = route(Entry(message, context))


  protected def route(entry: Entry): Unit = {
    logConfigService.config(entry).foreach {
      mapping =>
        if (mapping.filter.accept(entry)) {
          mapping.sink.flush(entry)
        }
    }
  }
}
