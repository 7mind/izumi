package izumi

import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import org.scalatest.WordSpec

class SafeTypeTest extends WordSpec {
  case class TestClass(field: Int)

  "SafeType" should {
    "have consistent equals and hashcode" in {
      val t1 = SafeType.get[Int]
      val t2 = SafeType.get[_root_.scala.Int]

      assert(t1 == t2)
      assert(t1.hashCode == t2.hashCode)
      assert(t1.toString == t2.toString)

      import u._

      val testClass = SafeType.get[TestClass]
      val rtype = testClass.use(_.decl(TermName("field")).asMethod.returnType)
      val t3 = SafeType(rtype)

      assert(t1 == t3)
      assert(t1.hashCode == t3.hashCode)
    }
  }

}
