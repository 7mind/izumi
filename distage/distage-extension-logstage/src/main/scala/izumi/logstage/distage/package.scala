package izumi.logstage

package object distage {
  @deprecated("renamed to LogIO2Module", "1.0")
  type LogBIOModule[F[_, _]] = LogIO2Module[F]
  @deprecated("renamed to LogIO2Module", "1.0")
  lazy val LogBIOModule: LogIO2Module.type = LogIO2Module

  @deprecated("renamed to LogIO3Module", "1.0")
  type LogBIO3Module[F[_, _, _]] = LogIO3Module[F]
  @deprecated("renamed to LogIO3Module", "1.0")
  lazy val LogBIO3Module: LogIO3Module.type = LogIO3Module
}
