package izumi.distage.testkit.runner.di

import izumi.distage.framework.services.ConfigLocationProvider
import java.util.concurrent.atomic.AtomicInteger

final class PlanningFixtureBootstrap(stage: PlanningFailureStage, configs: AtomicInteger, extensions: AtomicInteger, original: Throwable) extends PlanningFixtureBootstrapBase(stage, configs, extensions, original) {
  override protected def makeConfigLocationProvider(configBaseName: String): ConfigLocationProvider = { val _ = configBaseName; ConfigLocationProvider.Default }
}
