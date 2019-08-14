package com.github.pshirshov.izumi.logstage.api.rendering

import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.Log.LogArg

final case class RenderedParameter(
                                    arg: LogArg,
                                    repr: String,
                                    normalizedName: String
                                  ) {
  def value: Any = arg.value
}

final case class RenderedMessage(
                                  entry: Log.Entry
                                  , template: String
                                  , message: String
                                  , parameters: collection.Seq[RenderedParameter]
                                  , unbalanced: collection.Seq[RenderedParameter]
                                )
