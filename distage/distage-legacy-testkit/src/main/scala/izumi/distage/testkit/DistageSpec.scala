package izumi.distage.testkit

import izumi.distage.testkit.services.scalatest.adapter.DistageTestSupport
import izumi.fundamentals.reflection.Tags.TagK
import org.scalatest.{ScalatestSuite, WordSpecLike}

@deprecated("Use dstest", "2019/Jul/18")
abstract class DistageSpec[F[_]: TagK] extends DistageTestSupport[F] with WordSpecLike {
  override def toString: String = ScalatestSuite.suiteToString(None, this)
}



