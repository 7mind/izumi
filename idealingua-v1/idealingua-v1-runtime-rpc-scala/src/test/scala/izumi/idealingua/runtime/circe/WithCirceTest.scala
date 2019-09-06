package izumi.idealingua.runtime.circe

import io.circe.syntax._
import izumi.idealingua.runtime.circe.WithCirceTest.{Cba, Sealed}
import org.scalatest.WordSpec

final class WithCirceTest extends WordSpec {

  "WithCirce" should {
    "WithCirce works with case classes" in {
      assert(Cba(1, 2).asJson.as[Cba].right.get == Cba(1, 2))
      assert(Cba(1, 2).asJson.noSpaces == """{"a":1,"b":2}""")
    }

    "WithCirce works with (not-nested) sealed traits" in {
      assert(Sealed(Cba(1, 2)).asJson.as[Sealed].right.get == Sealed(Cba(1, 2)))
      assert(Sealed(Cba(1, 2)).asJson.noSpaces == """{"Sealed1":{"cba":{"a":1,"b":2}}}""")
    }
  }

}

object WithCirceTest {

  final case class Cba(a: Int, b: Int)
  object Cba extends IRTWithCirce[Cba]

  sealed trait Sealed
  object Sealed extends IRTWithCirce[Sealed] {
    def apply(cba: Cba): Sealed = Sealed1(cba)
  }

  final case class Sealed1(cba: Cba) extends Sealed
  object Sealed1 extends IRTWithCirce[Sealed1]
}

