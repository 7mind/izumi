package izumi.logstage.distage

import izumi.distage.model.definition.ModuleDef
import izumi.functional.mono.SyncSafe
import izumi.logstage.api.IzLogger
import izumi.reflect.{TagK, TagK3, TagKK}
import logstage.{LogCreateIO, LogIO, LogRouter, UnsafeLogIO}

/**
  * @param maybeRouter if `Some`, include a new [[LogstageModule]] based on the passed [[izumi.logstage.api.logger.LogRouter]]
  *                    if `None`, just add a [[logstage.LogIO]] binding
  *
  * @param setupStaticLogRouter if `true`, register the passed [[izumi.logstage.api.logger.LogRouter]] in [[izumi.logstage.api.routing.StaticLogRouter]]
  *                             (required for slf4j support)
  */
class LogIOModule[F[_]: TagK](
  maybeRouter: Option[LogRouter],
  setupStaticLogRouter: Boolean,
) extends ModuleDef {
  maybeRouter.foreach {
    router =>
      include(LogstageModule(router, setupStaticLogRouter))
  }

  make[LogIO[F]]
    .from(LogIO.fromLogger[F](_: IzLogger)(_: SyncSafe[F]))
    .aliased[UnsafeLogIO[F]]
    .aliased[LogCreateIO[F]]
}

object LogIOModule {
  @inline def apply[F[_]: TagK](router: LogRouter, setupStaticLogRouter: Boolean): LogIOModule[F] = new LogIOModule(Some(router), setupStaticLogRouter)
  @inline def apply[F[_]: TagK](): LogIOModule[F] = new LogIOModule(None, setupStaticLogRouter = false)
}

/** [[LogIOModule]] for bifunctors */
class LogBIOModule[F[_, _]: TagKK](
  maybeRouter: Option[LogRouter],
  setupStaticLogRouter: Boolean,
) extends LogIOModule[F[Nothing, ?]](maybeRouter, setupStaticLogRouter)

object LogBIOModule {
  @inline def apply[F[_, _]: TagKK](router: LogRouter, setupStaticLogRouter: Boolean): LogBIOModule[F] = new LogBIOModule(Some(router), setupStaticLogRouter)
  @inline def apply[F[_, _]: TagKK](): LogBIOModule[F] = new LogBIOModule(None, setupStaticLogRouter = false)
}

/** [[LogIOModule]] for trifunctors */
class LogBIO3Module[F[_, _, _]: TagK3](
  maybeRouter: Option[LogRouter],
  setupStaticLogRouter: Boolean,
) extends LogIOModule[F[Any, Nothing, ?]](maybeRouter, setupStaticLogRouter)

object LogBIO3Module {
  @inline def apply[F[_, _, _]: TagK3](router: LogRouter, setupStaticLogRouter: Boolean): LogBIO3Module[F] = new LogBIO3Module(Some(router), setupStaticLogRouter)
  @inline def apply[F[_, _, _]: TagK3](): LogBIO3Module[F] = new LogBIO3Module(None, setupStaticLogRouter = false)
}
