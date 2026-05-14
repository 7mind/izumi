package izumi.functional.bio

import izumi.functional.bio.IORunner1.{CatsDispatcherImpl, CatsIOImpl}
import izumi.fundamentals.orphans.{`cats.effect.IO`, `cats.effect.std.Dispatcher`, `cats.effect.unsafe.IORuntime`}

import scala.annotation.nowarn

private[bio] trait LowPriorityIORunner1Instances extends LowPriorityIORunner1Instances1 {

  implicit final def fromCatsDispatcher[F[_], Dispatcher[_[_]]: `cats.effect.std.Dispatcher`](implicit dispatcher: Dispatcher[F]): IORunner1[F] =
    new CatsDispatcherImpl[F]()(using dispatcher.asInstanceOf[cats.effect.std.Dispatcher[F]])
}

private[bio] trait LowPriorityIORunner1Instances1 {

  @nowarn("msg=package lang") /* 2.12 false shadowing warning on Java 25+ */
  implicit final def fromCatsIORuntime[IO[_]: `cats.effect.IO`, IORuntime: `cats.effect.unsafe.IORuntime`](implicit ioRuntime: IORuntime): IORunner1[IO] =
    new CatsIOImpl()(using ioRuntime.asInstanceOf[cats.effect.unsafe.IORuntime]).asInstanceOf[IORunner1[IO]]

}
