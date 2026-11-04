package izumi.distage.docker

trait ContainerDefTemplate extends ContainerDef {
  self: Singleton =>

  def image: String
  def version: String
}
