package org.bitbucket.pshirshov.izumi.logger

import com.ratoshniuk.izumi.Log
import org.bitbucket.pshirshov.izumi.config.ConfigLoader

case class LogMapping(filter: LogFilter, sink: LogSink)

trait LogFilter extends ConfigLoader { // this can be implemented in code as well, though later we will introduce declarative config

  // TODO : Remove when config will be enabled
  private val all : Set[Log.Level] = Set(Log.Level.Error, Log.Level.Warn, Log.Level.Debug, Log.Level.Info)


  private def contextIsValid(ctxt: Log.Context): Boolean = {
//    val className = ctxt.static.id
//    loggingConfig
//      .rules
//      .find(rule => className.startsWith(rule.packageName)) // TODO : speed up comparison
//      .map(_.levels).getOrElse(loggingConfig.default).contains(ctxt.dynamic.level)
//      .map(_.levels).getOrElse(all).contains(ctxt.dynamic.level)
    true
  }

  def accept(e: Log.Entry): Boolean = {
    contextIsValid(e.context)
  }
}
