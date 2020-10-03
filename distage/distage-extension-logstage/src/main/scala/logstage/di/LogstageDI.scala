package logstage.di

import izumi.logstage.distage

trait LogstageDI {
  type LogstageModule = distage.LogstageModule
  type LogIOModule[F[_]] = distage.LogIOModule[F]
  type LogBIOModule[F[_, _]] = distage.LogBIOModule[F]
  type LogBIO3Module[F[_, _, _]] = distage.LogBIO3Module[F]
}
