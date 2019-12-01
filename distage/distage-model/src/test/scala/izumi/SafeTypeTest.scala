package izumi

import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.fundamentals.reflection.Tags.{Tag, TagK}
import org.scalatest.WordSpec

class SafeTypeTest extends WordSpec {
  "SafeType" should {
    "have consistent equals and hashcode" in {
      val t1 = SafeType.get[Int]
      val t2 = SafeType.get[_root_.scala.Int]

      assert(t1 == t2)
      assert(t1.hashCode == t2.hashCode)
      assert(t1.toString == t2.toString)

      def mk[F[_]: TagK, T: Tag] = SafeType(Tag[F[T]].tag.typeArgs.head)
      val t3 = mk[List, Int]

      assert(t1 == t3)
      assert(t1.hashCode == t3.hashCode)
    }
  }

}

