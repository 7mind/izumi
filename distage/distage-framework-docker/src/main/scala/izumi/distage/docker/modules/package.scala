package izumi.distage.docker

import com.github.ghik.silencer.silent
import distage.TagK

package object modules {
  @silent("package object")
  @deprecated("DockerContainerModule has been renamed to `DockerSupportModule`", "old name will be deleted in 0.11.1")
  class DockerContainerModule[F[_]: TagK] extends DockerSupportModule[F]
  @deprecated("DockerContainerModule has been renamed to `DockerSupportModule`", "old name will be deleted in 0.11.1")
  lazy val DockerContainerModule: DockerSupportModule.type = DockerSupportModule
}
