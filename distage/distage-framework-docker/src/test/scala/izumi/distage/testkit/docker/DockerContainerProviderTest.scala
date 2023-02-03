package izumi.distage.testkit.docker

import distage.SafeType
import izumi.distage.docker.bundled.PostgresDocker
import izumi.distage.docker.impl.ContainerResource
import izumi.distage.model.definition.ModuleDef
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

final class DockerContainerProviderTest extends AnyWordSpec {
  "Return type is correct" in {
    new ModuleDef {
      assert(PostgresDocker.make[Identity].get.ret == SafeType.get[ContainerResource[Identity, PostgresDocker.Tag]])
    }
  }
}
