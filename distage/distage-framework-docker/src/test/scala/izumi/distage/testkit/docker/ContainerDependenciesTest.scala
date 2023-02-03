package izumi.distage.testkit.docker

import distage.*
import izumi.distage.docker.bundled.{KafkaDocker, KafkaDockerModule, ZookeeperDocker, ZookeeperDockerModule}
import izumi.distage.docker.impl.DockerClientWrapper
import izumi.distage.docker.impl.DockerClientWrapper.{ContainerDestroyMeta, RemovalReason}
import izumi.distage.docker.model.Docker.ContainerId
import izumi.distage.docker.modules.DockerSupportModule
import izumi.fundamentals.platform.functional.Identity
import logstage.IzLogger
import org.scalatest.wordspec.AnyWordSpec

final class ContainerDependenciesTest extends AnyWordSpec {
  "distage-docker should re-create containers with failed dependencies, https://github.com/7mind/izumi/issues/1366" in {
    val defn = PlannerInput(
      new ModuleDef {
        include(KafkaDockerModule[Identity])
        include(ZookeeperDockerModule[Identity])
        include(DockerSupportModule[Identity] overriddenBy DockerSupportModule.defaultConfig)
        make[IzLogger].fromValue(IzLogger())
      },
      Activation.empty,
      DIKey.get[KafkaDocker.Container],
      DIKey.get[ZookeeperDocker.Container],
      DIKey.get[ZookeeperDocker.Container],
      DIKey.get[DockerClientWrapper[Identity]],
    )

    def runContainers() = {
      Injector()
        .produce(defn).run[(ContainerId, ContainerId)] {
          (kafka: KafkaDocker.Container, zk: ZookeeperDocker.Container) =>
            (kafka.id, zk.id)
        }
    }

    val (k1, zk1) = runContainers()
    val (k11, zk11) = runContainers()

    assert(k11 == k1)
    assert(zk11 == zk1)

    Injector()
      .produce(defn).run {
        (client: DockerClientWrapper[Identity]) =>
          client.removeContainer(zk1, ContainerDestroyMeta.NoMeta, RemovalReason.AlreadyExited)
          ()
      }

    val (k2, zk2) = runContainers()

    assert(k1 != k2)
    assert(zk1 != zk2)
  }
}
