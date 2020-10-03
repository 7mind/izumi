package izumi.logstage.distage

import izumi.distage.model.definition.BootstrapModuleDef
import izumi.functional.bio.{SyncSafe2, SyncSafe3}
import izumi.functional.mono.SyncSafe
import izumi.reflect.{TagK, TagK3, TagKK}
import logstage.{LogCreateIO, LogIO, LogRouter, UnsafeLogIO}

class LogIOModule[F[_]: SyncSafe: TagK](
  router: LogRouter,
  setupStaticLogRouter: Boolean,
) extends BootstrapModuleDef {
  include(LogstageModule(router, setupStaticLogRouter))

  make[LogIO[F]]
    .from(LogIO.fromLogger[F](_))
    .aliased[UnsafeLogIO[F]]
    .aliased[LogCreateIO[F]]
}

class LogBIOModule[F[_, _]: SyncSafe2: TagKK](router: LogRouter, setupStaticLogRouter: Boolean) extends LogIOModule[F[Nothing, ?]](router, setupStaticLogRouter)

class LogBIO3Module[F[_, _, _]: SyncSafe3: TagK3](router: LogRouter, setupStaticLogRouter: Boolean) extends LogIOModule[F[Any, Nothing, ?]](router, setupStaticLogRouter)

object LogIOModule {
  @inline def apply[F[_]: SyncSafe: TagK](router: LogRouter, setupStaticLogRouter: Boolean): LogIOModule[F] = new LogIOModule(router, setupStaticLogRouter)
}
object LogBIOModule {
  @inline def apply[F[_, _]: SyncSafe2: TagKK](router: LogRouter, setupStaticLogRouter: Boolean): LogBIOModule[F] = new LogBIOModule(router, setupStaticLogRouter)
}
object LogBIO3Module {
  @inline def apply[F[_, _, _]: SyncSafe3: TagK3](router: LogRouter, setupStaticLogRouter: Boolean): LogBIO3Module[F] = new LogBIO3Module(router, setupStaticLogRouter)
}
