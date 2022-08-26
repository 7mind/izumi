package izumi.distage.testkit.scalatest

import cats.effect.IO
import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.Assertion

/** scalatest assertion macro for [[cats.effect.IO]] */
trait AssertCIO extends AssertCIOImpl {}

object AssertCIO extends AssertCIO {}
