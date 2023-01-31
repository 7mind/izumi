package izumi.functional.bio.test

import izumi.functional.bio.{Exit, UnsafeRun2}
import org.scalatest.wordspec.AsyncWordSpec
import zio.internal.Platform

class UnsafeRunTest extends AsyncWordSpec {

  "BIO" should {
    "be able to run on ZIO" in {
      val r = UnsafeRun2.createZIO(Platform.fromExecutionContext(implicitly), ())

      r.unsafeRunFuture(zio.IO.foreachPar(List(1, 2, 3))(a => zio.IO(a * 2))).map {
        case Exit.Success(value) =>
          assert(value == (2, 4, 6))
        case _: Exit.Failure[?] =>
          fail()
      }
    }
  }
}
