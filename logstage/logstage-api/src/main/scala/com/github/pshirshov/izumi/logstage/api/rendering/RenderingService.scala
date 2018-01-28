package com.github.pshirshov.izumi.logstage.api.rendering

import com.github.pshirshov.izumi.logstage.model.Log

object RenderingService {
  def render(entry: Log.Entry): RenderedMessage = {
    val templateBuilder = new StringBuilder()
    val messageBuilder = new StringBuilder()

    templateBuilder.append(entry.message.template.parts.head)
    messageBuilder.append(entry.message.template.parts.head)

    entry.message.template.parts.tail.zip(entry.message.args).foreach {
      case (part, (argName, argValue)) =>
        templateBuilder.append("{")
        templateBuilder.append(argName)
        templateBuilder.append("}")
        templateBuilder.append(part)

        messageBuilder.append("{")
        messageBuilder.append(argName)
        messageBuilder.append('=')
        messageBuilder.append(argValue.toString)
        messageBuilder.append("}")
        messageBuilder.append(part)
    }
    RenderedMessage(entry, templateBuilder.toString(), messageBuilder.toString())
  }

}
