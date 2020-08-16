package leaderboard.plugins

import java.util.concurrent.ThreadPoolExecutor

import cats.effect.{Async, Blocker, Bracket, ConcurrentEffect, ContextShift, Timer}
import distage.Id
import distage.plugins.PluginDef
import izumi.distage.effect.modules.ZIODIEffectModule
import logstage.LogBIO
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.{IO, Task}

import scala.concurrent.ExecutionContext

object ZIOPlugin extends PluginDef {
  include(ZIODIEffectModule)

  addImplicit[Bracket[Task, Throwable]]
  addImplicit[Async[Task]]
  addImplicit[ContextShift[Task]]
  addImplicit[Timer[Task]]
  make[ConcurrentEffect[Task]].from {
    runtime: zio.Runtime[Any] =>
      taskEffectInstance(runtime)
  }

  make[Blocker].from {
    pool: ThreadPoolExecutor @Id("zio.io") =>
      Blocker.liftExecutionContext(ExecutionContext.fromExecutorService(pool))
  }

  make[LogBIO[IO]].from(LogBIO.fromLogger[IO] _)
}
