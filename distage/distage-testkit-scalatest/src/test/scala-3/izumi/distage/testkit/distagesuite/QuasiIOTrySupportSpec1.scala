package izumi.distage.testkit.distagesuite

import izumi.distage.modules.DefaultModule
import izumi.distage.modules.support.unsafe.TrySupport
import izumi.distage.testkit.scalatest.Spec1
import izumi.reflect.TagK

import scala.util.Try

private object QuasiIOTryDefaultModule {
  import TrySupport.{quasiAsyncTry, quasiIORunnerTry, quasiIOTry, quasiTemporalTry}

  val module: DefaultModule[Try] = DefaultModule.fromQuasiIO[Try]
}

final class QuasiIOTrySupportSpec1 extends Spec1[Try]()(using summon[TagK[Try]], QuasiIOTryDefaultModule.module) {
  "Spec1[Try]" should {
    "run a trivial test with the generic QuasiIO default module" in {
      assert(1 + 1 == 2)
    }
  }
}
