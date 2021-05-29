package izumi.logstage.distage

import izumi.distage.model.definition.ModuleDef
import izumi.functional.mono.SyncSafe
import izumi.logstage.api.IzLogger
import izumi.reflect.{TagK, TagK3, TagKK}
import logstage.{LogCreateIO, LogIO, LogRouter, UnsafeLogIO}

/**
  * Add a `LogIO[F]` component and others, depending on an existing `IzLogger`
  *
  * To setup `IzLogger` at the same time, use `apply` with parameters
  */
class LogIOModule[F[_]: TagK] extends ModuleDef {
  make[LogIO[F]]
    .from(LogIO.fromLogger[F](_: IzLogger)(_: SyncSafe[F]))
    .aliased[UnsafeLogIO[F]]
    .aliased[LogCreateIO[F]]
}

object LogIOModule {
  @inline def apply[F[_]: TagK](): LogIOModule[F] = new LogIOModule
  /**
    * Setup `IzLogger` component based on the passed `router` and add a `LogIO` wrapper for it
    *
    * @param setupStaticLogRouter if `true`, register the passed [[izumi.logstage.api.logger.LogRouter]]
    *                             in [[izumi.logstage.api.routing.StaticLogRouter]] (required for slf4j support)
    */
  @inline def apply[F[_]: TagK](router: LogRouter, setupStaticLogRouter: Boolean): LogIOModule[F] = new LogIOModule {
    include(LogstageModule(router, setupStaticLogRouter))
  }
}

/** [[LogIOModule]] for bifunctors */
class LogIO2Module[F[_, _]: TagKK] extends LogIOModule[F[Nothing, `?`]]

object LogIO2Module {
  @inline def apply[F[_, _]: TagKK](): LogIO2Module[F] = new LogIO2Module
  @inline def apply[F[_, _]: TagKK](router: LogRouter, setupStaticLogRouter: Boolean): LogIO2Module[F] = new LogIO2Module {
    include(LogstageModule(router, setupStaticLogRouter))
  }
}

/** [[LogIOModule]] for trifunctors */
class LogIO3Module[F[_, _, _]: TagK3] extends LogIOModule[F[Any, Nothing, `?`]]

object LogIO3Module {
  @inline def apply[F[_, _, _]: TagK3](): LogIO3Module[F] = new LogIO3Module
  @inline def apply[F[_, _, _]: TagK3](router: LogRouter, setupStaticLogRouter: Boolean): LogIO3Module[F] = new LogIO3Module {
    include(LogstageModule(router, setupStaticLogRouter))
  }
}
