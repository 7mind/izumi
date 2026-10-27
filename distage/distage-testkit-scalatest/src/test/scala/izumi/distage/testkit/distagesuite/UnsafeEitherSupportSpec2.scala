package izumi.distage.testkit.distagesuite

import izumi.distage.modules.support.unsafe.EitherSupport.defaultModuleEither
import izumi.distage.modules.support.unsafe.TrySupport.defaultModuleTry
import izumi.distage.testkit.scalatest.{Spec1, Spec2}

import scala.util.Try

final class UnsafeEitherSupportSpec2 extends Spec2[Either] {
  "Spec2[Either]" should {
    "run a trivial test with the unsafe default module" in {
      assert(1 + 1 == 2)
    }
  }
}

final class UnsafeTrySupportSpec1 extends Spec1[Try] {
  "Spec1[Try]" should {
    "run a trivial test with the unsafe default module" in {
      assert(1 + 1 == 2)
    }
  }
}
