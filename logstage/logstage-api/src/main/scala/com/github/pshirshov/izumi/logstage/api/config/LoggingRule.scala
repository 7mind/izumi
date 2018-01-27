package com.github.pshirshov.izumi.logstage.api.config

import com.github.pshirshov.izumi.logstage.model.Log

case class LoggingRule(packageName: String = "", levels: Set[Log.Level])
