package izumi.functional.quasi

import izumi.functional.quasi.QuasiIORunner.{CatsDispatcherImpl, CatsIOImpl}
import izumi.fundamentals.orphans.{`cats.effect.IO`, `cats.effect.std.Dispatcher`, `cats.effect.unsafe.IORuntime`}

import scala.annotation.nowarn

private[quasi] trait LowPriorityQuasiIORunnerInstances extends LowPriorityQuasiIORunnerInstances1 {

  implicit final def fromCatsDispatcher[F[_], Dispatcher[_[_]]: `cats.effect.std.Dispatcher`](implicit dispatcher: Dispatcher[F]): QuasiIORunner[F] =
    new CatsDispatcherImpl[F]()(using dispatcher.asInstanceOf[cats.effect.std.Dispatcher[F]])
}

private[quasi] trait LowPriorityQuasiIORunnerInstances1 {

  @nowarn("msg=package lang") /* 2.12 false shadowing warning on Java 25+ */
  implicit final def fromCatsIORuntime[IO[_]: `cats.effect.IO`, IORuntime: `cats.effect.unsafe.IORuntime`](implicit ioRuntime: IORuntime): QuasiIORunner[IO] =
    new CatsIOImpl()(using ioRuntime.asInstanceOf[cats.effect.unsafe.IORuntime]).asInstanceOf[QuasiIORunner[IO]]

}
