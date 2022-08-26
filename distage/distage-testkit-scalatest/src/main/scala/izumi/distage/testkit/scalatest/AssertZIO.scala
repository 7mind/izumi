package izumi.distage.testkit.scalatest

/** scalatest assertion macro for [[zio.ZIO]] */
trait AssertZIO extends AssertZIOImpl {}

object AssertZIO extends AssertZIO {}
