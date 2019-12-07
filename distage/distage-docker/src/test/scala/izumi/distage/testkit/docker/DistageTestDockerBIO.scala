package izumi.distage.testkit.docker

import distage.DIKey
import izumi.distage.docker.examples.{DynamoDocker, PostgresDocker}
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.docker.fixtures.PgSvcExample
import izumi.distage.testkit.scalatest.DistageBIOSpecScalatest
import zio.IO

final class DistageTestDockerBIO extends DistageBIOSpecScalatest[IO] {

  def isCI: Boolean = System.getenv().containsKey("CI_BRANCH")

  // ignore docker tests on CI (nested docker trouble)
  if (!isCI) {
    "distage test runner" should {
      "support docker resources" in {
        service: PgSvcExample =>
          for {
            _ <- IO(println(s"ports/1: pg=${service.pg} ddb=${service.ddb} "))
          } yield ()
      }

      "support memoization" in {
        service: PgSvcExample =>
          for {
            _ <- IO(println(s"ports/2: pg=${service.pg} ddb=${service.ddb} "))
          } yield ()
      }
    }
  }

  override protected def config: TestConfig = {
    TestConfig(
      memoizedKeys = Set(
        DIKey.get[DynamoDocker.Container],
        DIKey.get[PostgresDocker.Container],
      ))
  }
}
