package logstage.di

import izumi.logstage.distage

trait LogstageDI {
  @deprecated("renamed to `izumi.logstage.distage.LogstageModule", "1.0")
  type LogstageModule = distage.LogstageModule
}
