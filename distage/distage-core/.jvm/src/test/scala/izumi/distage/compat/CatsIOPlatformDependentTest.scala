package izumi.distage.compat

import cats.effect.IO
import cats.effect.unsafe.IORuntime

trait CatsIOPlatformDependentTest {
  protected[this] def catsIOUnsafeRunSync[A](io: IO[A]): A = io.unsafeRunSync()(IORuntime.global)
}
