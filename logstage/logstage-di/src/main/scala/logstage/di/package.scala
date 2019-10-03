package logstage

import izumi.logstage.distage

package object di extends LogstageDI {
  override type LogstageModule = distage.LogstageModule
}
