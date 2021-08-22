package izumi.logstage.api.rendering

import izumi.logstage.api.Log

trait LogEntryTransformer {
  def apply(log: Log.Entry): Log.Entry
}
