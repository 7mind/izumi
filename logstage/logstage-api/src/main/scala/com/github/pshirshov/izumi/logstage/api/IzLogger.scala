package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.model.Log.CustomContext
import com.github.pshirshov.izumi.logstage.model.logger.LogRouter
import com.github.pshirshov.izumi.logstage.model.{AbstractLogger, Log}

import scala.language.implicitConversions


class IzLogger
(
  override val receiver: LogRouter
  , override val contextCustom: Log.CustomContext
) extends LoggingMacro
  with AbstractLogger {

  implicit def withCustomContext(newCustomContext: CustomContext): IzLogger = {
    new IzLogger(receiver, contextCustom + newCustomContext)
  }

  implicit def withMapAsCustomContext(map: Map[String, Any]): IzLogger = {
    withCustomContext(CustomContext(map.toList))
  }

  def apply[V](conv: Map[String, V]): IzLogger = conv

  def apply[V](elems: (String, V)*): IzLogger = elems.toMap

}

object IzLogger {
  def apply(receiver: LogRouter): IzLogger = {
    new IzLogger(receiver, CustomContext.empty)
  }
}