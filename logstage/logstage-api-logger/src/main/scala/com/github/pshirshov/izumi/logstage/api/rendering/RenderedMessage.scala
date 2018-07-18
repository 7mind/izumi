package com.github.pshirshov.izumi.logstage.api.rendering

import com.github.pshirshov.izumi.logstage.api.Log

final case class RenderedParameter(value: Any, repr: String)

final case class RenderedMessage(
                                  entry: Log.Entry
                                  , template: String
                                  , message: String
                                  , parameters: Map[String, Seq[RenderedParameter]]
                                )
