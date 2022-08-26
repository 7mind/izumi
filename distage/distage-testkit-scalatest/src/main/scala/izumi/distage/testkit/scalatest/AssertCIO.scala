package izumi.distage.testkit.scalatest

/** scalatest assertion macro for [[cats.effect.IO]] */
trait AssertCIO extends AssertCIOImpl {}

object AssertCIO extends AssertCIO {}
