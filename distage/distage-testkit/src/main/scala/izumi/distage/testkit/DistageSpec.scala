package izumi.distage.testkit

import izumi.fundamentals.reflection.Tags.TagK
import izumi.distage.testkit.services.st.adapter.DistageTestSupport
import org.scalatest.{ScalatestSuite, WordSpecLike}

@deprecated("Use dstest", "2019/Jul/18")
abstract class DistageSpec[F[_]: TagK] extends DistageTestSupport[F] with WordSpecLike {
  override def toString: String = ScalatestSuite.suiteToString(None, this)
}



