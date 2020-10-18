package logstage

import izumi.logstage.distage

package object di extends LogstageDI {
  @deprecated("renamed to `izumi.logstage.distage.LogstageModule", "1.0")
  override type LogstageModule = distage.LogstageModule
}
