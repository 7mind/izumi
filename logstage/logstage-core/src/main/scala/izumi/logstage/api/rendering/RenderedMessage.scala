package izumi.logstage.api.rendering

import izumi.logstage.api.Log
import izumi.logstage.api.Log.LogArg

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
