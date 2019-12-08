package izumi.distage.testkit

import izumi.distage.testkit.services.scalatest.adapter.DistageTestSupport
import izumi.fundamentals.reflection.Tags.TagK
import org.scalatest.ScalatestSuite
import org.scalatest.wordspec.AnyWordSpecLike

@deprecated("Use dstest", "2019/Jul/18")
abstract class DistageSpec[F[_]: TagK] extends DistageTestSupport[F] with AnyWordSpecLike {
  override def toString: String = ScalatestSuite.suiteToString(None, this)
}



