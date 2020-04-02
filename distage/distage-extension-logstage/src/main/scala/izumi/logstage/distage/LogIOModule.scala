package izumi.logstage.distage

import distage.{BootstrapModuleDef, TagK, TagK3, TagKK}
import izumi.functional.bio.{SyncSafe2, SyncSafe3}
import izumi.functional.mono.SyncSafe
import logstage.{LogIO, LogRouter}

class LogBIO3Module[F[_, _, _]: SyncSafe3: TagK3](router: LogRouter, setupStaticLogRouter: Boolean) extends LogIOModule[F[Any, Nothing, ?]](router, setupStaticLogRouter)

class LogBIOModule[F[_, _]: SyncSafe2: TagKK](router: LogRouter, setupStaticLogRouter: Boolean) extends LogIOModule[F[Nothing, ?]](router, setupStaticLogRouter)

class LogIOModule[F[_]: SyncSafe: TagK](router: LogRouter, setupStaticLogRouter: Boolean) extends BootstrapModuleDef {
  include(new LogstageModule(router, setupStaticLogRouter))

  make[LogIO[F]].from(LogIO.fromLogger(_))
}
