package izumi.distage.testkit.runner.di

import java.util.concurrent.atomic.AtomicInteger

final class PlanningFixtureBootstrap(stage: PlanningFailureStage, configs: AtomicInteger, extensions: AtomicInteger, original: Throwable) extends PlanningFixtureBootstrapBase(stage, configs, extensions, original)
