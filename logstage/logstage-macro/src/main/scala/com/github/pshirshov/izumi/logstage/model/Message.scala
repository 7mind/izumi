package com.github.pshirshov.izumi.logstage.model

case class Message(template: StringContext, args: List[(String, Any)])
