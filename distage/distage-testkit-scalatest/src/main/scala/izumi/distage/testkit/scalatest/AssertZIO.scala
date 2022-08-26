package izumi.distage.testkit.scalatest

import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.Assertion
import zio.IO

/** scalatest assertion macro for [[zio.ZIO]] */
trait AssertZIO extends AssertZIOImpl {}

object AssertZIO extends AssertZIO {}
