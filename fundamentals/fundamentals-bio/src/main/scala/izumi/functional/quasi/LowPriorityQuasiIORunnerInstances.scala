package izumi.functional.quasi

import izumi.functional.quasi.QuasiIORunner.{CatsDispatcherImpl, CatsIOImpl}
import izumi.fundamentals.orphans.{`cats.effect.IO`, `cats.effect.std.Dispatcher`, `cats.effect.unsafe.IORuntime`}

private[quasi] trait LowPriorityQuasiIORunnerInstances extends LowPriorityQuasiIORunnerInstances1 {

  implicit final def fromCatsDispatcher[F[_], Dispatcher[_[_]]: `cats.effect.std.Dispatcher`](implicit dispatcher: Dispatcher[F]): QuasiIORunner[F] =
    new CatsDispatcherImpl[F]()(using dispatcher.asInstanceOf[cats.effect.std.Dispatcher[F]])

  def mkFromCatsDispatcher[F[_]](dispatcher: cats.effect.std.Dispatcher[F]): QuasiIORunner[F] = new CatsDispatcherImpl[F]()(using dispatcher)
}

private[quasi] trait LowPriorityQuasiIORunnerInstances1 {

  implicit final def fromCatsIORuntime[IO[_]: `cats.effect.IO`, IORuntime: `cats.effect.unsafe.IORuntime`](implicit ioRuntime: IORuntime): QuasiIORunner[IO] =
    new CatsIOImpl()(using ioRuntime.asInstanceOf[cats.effect.unsafe.IORuntime]).asInstanceOf[QuasiIORunner[IO]]

  def mkFromCatsIORuntime(ioRuntime: cats.effect.unsafe.IORuntime): QuasiIORunner[cats.effect.IO] = new CatsIOImpl()(using ioRuntime)
}
