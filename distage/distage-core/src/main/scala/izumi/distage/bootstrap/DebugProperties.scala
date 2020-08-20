package izumi.distage.bootstrap

import izumi.fundamentals.platform.properties

object DebugProperties extends properties.DebugProperties {
  /** Print debug messages when planning Injector's own bootstrap environment */
  final val `izumi.distage.debug.bootstrap` = Property("izumi.distage.debug.bootstrap")

  /** Print full stacktraces for all exceptions in user code during plan interpretation. default: `true` */
  final val `izumi.distage.interpreter.full-stacktraces` = Property("izumi.distage.interpreter.full-stacktraces")

  /** Initialize proxies for circular dependencies as soon as possible. default: `true` */
  final val `izumi.distage.init-proxies-asap` = Property("izumi.distage.init-proxies-asap")
}
