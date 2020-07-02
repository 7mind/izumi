package izumi.distage.docker

import scala.annotation.nowarn
import distage.TagK

package object modules {
  @nowarn("msg=package object")
  @deprecated("DockerContainerModule has been renamed to `DockerSupportModule`", "old name will be deleted in 0.11.1")
  class DockerContainerModule[F[_]: TagK] extends DockerSupportModule[F]
  @deprecated("DockerContainerModule has been renamed to `DockerSupportModule`", "old name will be deleted in 0.11.1")
  lazy val DockerContainerModule: DockerSupportModule.type = DockerSupportModule
}
