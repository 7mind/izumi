package izumi.functional.bio.test

import izumi.functional.bio.{Exit, UnsafeRun2}
import org.scalatest.wordspec.AsyncWordSpec
import zio.Executor

class UnsafeRunTest extends AsyncWordSpec {

  "BIO" should {
    "be able to run on ZIO" in {
      val r = UnsafeRun2.createZIO(customCpuPool = Some(Executor.fromExecutionContext(this.executionContext)))

      r.unsafeRunAsyncAsFuture(zio.ZIO.foreachPar(List(1, 2, 3))(a => zio.ZIO.attempt(a * 2))).map {
        case Exit.Success(value) =>
          assert(value == List(2, 4, 6))
        case _: Exit.Failure[?] =>
          fail()
      }
    }
  }
}
