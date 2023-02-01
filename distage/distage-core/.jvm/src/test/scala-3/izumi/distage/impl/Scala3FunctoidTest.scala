package izumi.distage.impl

import izumi.distage.model.definition.Id
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.{DIKey, TypedRef}
import org.scalatest.wordspec.AnyWordSpec

// TODO: This test won't work on SJS until this issue is fixed: https://github.com/lampepfl/dotty/issues/16801
class Scala3FunctoidTest extends AnyWordSpec {
  "Functoid on Scala 3" should {

    "support more than 22 args in lambda" in {
      val fn = Functoid.apply(
        (
          a1: Int,
          a2: Int,
          a3: Int,
          a4: Int,
          a5: Int,
          a6: Int,
          a7: Int,
          a8: Int,
          a9: Int,
          a10: Int,
          a11: Int,
          a12: Int,
          a13: Int,
          a14: Int,
          a15: Int,
          a16: Int,
          a17: Int,
          a18: Int,
          a19: Int,
          a20: Int @Id("abc"),
          a21: Int,
          a22: Int,
          a23: Int,
          a24: Int,
          a25: Int,
          a26: Int,
          a27: Int,
          a28: Int,
          a29: Int,
          a30: Int,
          a31: Int,
          a32: Int,
        ) =>
          a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12 + a13 + a14 + a15 + a16 + a17 + a18 + a19 + a20 + a21 + a22 + a23 + a24 + a25 + a26 + a27 + a28 + a29 + a30 + a31 + a32
      )

      assert(fn.get.parameters.map(_.key).contains(DIKey.get[Int].named("abc")))
      val args = 1 to 32
      assert(fn.get.unsafeApply(args.map(TypedRef(_))) == args.sum)
    }
  }

}
