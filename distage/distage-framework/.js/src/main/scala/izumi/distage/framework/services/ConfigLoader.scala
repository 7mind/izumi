package izumi.distage.framework.services

trait ConfigLoader extends AbstractConfigLoader {}

object ConfigLoader {
  def empty: ConfigLoader = () => ???
}
