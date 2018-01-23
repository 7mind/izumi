package org.bitbucket.pshirshov.izumi.logger

import org.bitbucket.pshirshov.izumi.config.ConfigLoader

trait LogFilter extends ConfigLoader { // this can be implemented in code as well, though later we will introduce declarative config
  private def contextIsValid(ctxt: Log.Context): Boolean = {
    val className = ctxt.static.id
    loggingConfig
      .rules
      .find(rule => className.startsWith(rule.packageName)) // TODO : speed up comparison
      .map(_.levels).getOrElse(loggingConfig.default).contains(ctxt.dynamic.level)
  }

  def accept(e: Log.Entry): Boolean = {
    contextIsValid(e.context)
  }
}
