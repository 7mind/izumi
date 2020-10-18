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
class LogIO2Module[F[_, _]: TagKK](
  maybeRouter: Option[LogRouter],
  setupStaticLogRouter: Boolean,
) extends LogIOModule[F[Nothing, ?]](maybeRouter, setupStaticLogRouter)

object LogIO2Module {
  @inline def apply[F[_, _]: TagKK](router: LogRouter, setupStaticLogRouter: Boolean): LogIO2Module[F] = new LogIO2Module(Some(router), setupStaticLogRouter)
  @inline def apply[F[_, _]: TagKK](): LogIO2Module[F] = new LogIO2Module(None, setupStaticLogRouter = false)
}

/** [[LogIOModule]] for trifunctors */
class LogIO3Module[F[_, _, _]: TagK3](
  maybeRouter: Option[LogRouter],
  setupStaticLogRouter: Boolean,
) extends LogIOModule[F[Any, Nothing, ?]](maybeRouter, setupStaticLogRouter)

object LogIO3Module {
  @inline def apply[F[_, _, _]: TagK3](router: LogRouter, setupStaticLogRouter: Boolean): LogIO3Module[F] = new LogIO3Module(Some(router), setupStaticLogRouter)
  @inline def apply[F[_, _, _]: TagK3](): LogIO3Module[F] = new LogIO3Module(None, setupStaticLogRouter = false)
}
