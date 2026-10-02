package izumi.distage.testkit.runner.impl.services

import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.Activation
import izumi.distage.roles.launcher.{ActivationParser, RoleAppActivationParser}
import izumi.distage.testkit.model.{TestActivationStrategy, TestEnvironment}
import izumi.fundamentals.platform.cli.model.RoleAppArgs
import izumi.logstage.api.IzLogger

final class TestActivationResolver {
  def resolve(config: AppConfig, env: TestEnvironment, logger: IzLogger): Activation = env.activationStrategy match {
    case TestActivationStrategy.IgnoreConfig => env.activation
    case TestActivationStrategy.LoadConfig(ignoreUnknown, warnUnset) =>
      val roleAppActivationParser = new RoleAppActivationParser.Impl(logger, ignoreUnknown)
      val activationParser = new ActivationParser.Impl(roleAppActivationParser, RoleAppArgs.empty, env.activationInfo, env.activation, Activation.empty, logger, warnUnset)
      activationParser.parseActivation(config) ++ env.activation
  }
}
