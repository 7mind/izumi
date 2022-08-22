package izumi.logstage.api.logger


trait AbstractMacroLogger { this: AbstractLogger =>

  /**
    * More efficient aliases for [[log]]
    *
    * These directly splice an [[acceptable]] check before calling [[unsafeLog]] which is more efficient than
    * creating a `messageThunk` for a [[log]] call.
    *
    * They also look better in Intellij
    */
  final def trace(message: String): Unit = ???
  final def debug(message: String): Unit = ???
  final def info(message: String): Unit = ???
  final def warn(message: String): Unit = ???
  final def error(message: String): Unit = ???
  final def crit(message: String): Unit = ???
}
