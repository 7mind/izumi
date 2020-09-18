package izumi.functional.bio.impl

import izumi.functional.bio.{BIOFiber, BIOFork}
import monix.bio.IO

object BIOForkMonix extends BIOForkMonix

class BIOForkMonix extends BIOFork[monix.bio.IO] {
  override def fork[R, E, A](f: IO[E, A]): IO[Nothing, BIOFiber[IO, E, A]] = {
    f.start.map(BIOFiber.fromMonix)
  }
}
