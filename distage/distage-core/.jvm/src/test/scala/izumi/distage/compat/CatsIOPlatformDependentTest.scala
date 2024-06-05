package izumi.distage.compat

import cats.effect.IO
import cats.effect.unsafe.IORuntime

trait CatsIOPlatformDependentTest {
  protected def catsIOUnsafeRunSync[A](io: IO[A]): A = io.unsafeRunSync()(IORuntime.global)
}
