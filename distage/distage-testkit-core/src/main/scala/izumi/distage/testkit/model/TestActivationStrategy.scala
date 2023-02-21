package izumi.distage.testkit.model

sealed trait TestActivationStrategy

object TestActivationStrategy {
  case object IgnoreConfig extends TestActivationStrategy

  case class LoadConfig(ignoreUnknown: Boolean, warnUnset: Boolean) extends TestActivationStrategy
}
