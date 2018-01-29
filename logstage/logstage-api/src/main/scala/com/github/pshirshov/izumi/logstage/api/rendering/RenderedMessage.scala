package com.github.pshirshov.izumi.logstage.api.rendering

import com.github.pshirshov.izumi.logstage.model.Log

case class RenderedMessage(entry: Log.Entry, template: String, message: String)
