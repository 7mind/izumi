package izumi.distage.testkit.docker

import distage.DIKey
import izumi.distage.docker.model.Docker.AvailablePort
import izumi.distage.model.definition.Id
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.TestConfig.ParallelLevel
import izumi.distage.testkit.docker.fixtures.PgSvcExample
import izumi.distage.testkit.scalatest.Spec2
import izumi.logstage.api.Log
import logstage.LogIO2
import zio.IO

abstract class ContainerDependenciesTest extends Spec2[IO] {

  override protected def config: TestConfig = super.config.copy(
    memoizationRoots = Set(DIKey[PgSvcExample]),
    parallelTests = ParallelLevel.Unlimited,
    parallelEnvs = ParallelLevel.Unlimited,
    logLevel = Log.Level.Info,
  )

  "distage test runner should start only one container for reusable" should {

    "support docker resources" in {
      // TODO: additionally check flyway outcome with doobie
      (kafka: AvailablePort @Id("kafka"), log: LogIO2[IO]) =>
        for {
          _ <- log.info(s"Kafka port: $kafka")
        } yield ()
    }

  }
}

final class ContainerDependenciesTestImpl extends ContainerDependenciesTest
