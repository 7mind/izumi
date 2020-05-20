package izumi.distage.docker

package object modules {
  @deprecated("DockerContainerModule has been renamed to `DockerSupportModule`", "old name will be deleted in 0.11.1")
  type DockerContainerModule[F[_]] = DockerSupportModule[F]
  @deprecated("DockerContainerModule has been renamed to `DockerSupportModule`", "old name will be deleted in 0.11.1")
  lazy val DockerContainerModule: DockerSupportModule.type = DockerSupportModule
}
