package izumi.logstage.api.logger


trait AbstractMacroLogIO[F[_]] { this: AbstractLogIO[F] =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  final def trace(message: String): F[Unit] = ???
  final def debug(message: String): F[Unit] = ???
  final def info(message: String): F[Unit] = ???
  final def warn(message: String): F[Unit] = ???
  final def error(message: String): F[Unit] = ???
  final def crit(message: String): F[Unit] = ???
}
