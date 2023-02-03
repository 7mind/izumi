package izumi.distage.testkit.docker

import distage.*
import izumi.distage.docker.bundled.{KafkaDocker, KafkaDockerModule, ZookeeperDocker, ZookeeperDockerModule}
import izumi.distage.docker.impl.DockerClientWrapper
import izumi.distage.docker.impl.DockerClientWrapper.{ContainerDestroyMeta, DockerIntegrationCheck, RemovalReason}
import izumi.distage.docker.model.Docker.ContainerId
import izumi.distage.docker.modules.DockerSupportModule
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import logstage.IzLogger
import org.scalatest.wordspec.AnyWordSpec

final class ContainerDependenciesTest extends AnyWordSpec {
  "distage-docker should re-create containers with failed dependencies, https://github.com/7mind/izumi/issues/1366" in {
    val module = new ModuleDef {
      include(KafkaDockerModule[Identity])
      include(ZookeeperDockerModule[Identity])
      include(DockerSupportModule.default[Identity])
      make[IzLogger].fromValue(IzLogger())
    }

    Injector()
      .produceRun(module) {
        (check: DockerIntegrationCheck[Identity]) =>
          assume(check.resourcesAvailable() == ResourceCheck.Success())
      }

    def runContainers(): (ContainerId, ContainerId) = {
      Injector()
        .produceRun(module) {
          (kafka: KafkaDocker.Container, zk: ZookeeperDocker.Container) =>
            (kafka.id, zk.id): Identity[(ContainerId, ContainerId)]
        }
    }

    val (k1, zk1) = runContainers()
    val (k11, zk11) = runContainers()

    assert(k11 == k1)
    assert(zk11 == zk1)

    Injector()
      .produceRun(module) {
        (client: DockerClientWrapper[Identity]) =>
          client.removeContainer(zk1, ContainerDestroyMeta.NoMeta, RemovalReason.AlreadyExited)
          ()
      }

    val (k2, zk2) = runContainers()

    assert(k1 != k2)
    assert(zk1 != zk2)
  }
}
