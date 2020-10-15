package izumi.functional.bio.impl

import izumi.functional.bio.{Fiber2, Fork2}
import monix.bio.IO

object ForkMonix extends ForkMonix

class ForkMonix extends Fork2[monix.bio.IO] {
  override def fork[R, E, A](f: IO[E, A]): IO[Nothing, Fiber2[IO, E, A]] = {
    f.start.map(Fiber2.fromMonix)
  }
}
