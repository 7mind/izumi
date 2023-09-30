package izumi.functional.bio.test

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.scalatest.wordspec.AsyncWordSpec

class QuasiIOTest extends AsyncWordSpec {
  implicit val rt: IORuntime = IORuntime.builder().setCompute(implicitly, () => ()).setBlocking(implicitly, () => ()).build()

  "QuasiIO" should {
    "be convertable to Future" in {
      val a: cats.effect.IO[Int] = IO(2)
      val f1 = a.unsafeToFuture()
      f1.map(i => assert(i == 2))
    }
  }
}
