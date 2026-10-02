package izumi.distage.testkit.spec

import izumi.distage.model.definition.{Activation, Module, ModuleBase, ModuleDef}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoader, PluginLoaderDefaultImpl}
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.{TestConfig, TestEnvironment}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.*
import izumi.reflect.TagK

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object SessionEnvironmentFixtures {
  private final val ConcurrentRequests = 32

  def main(args: Array[String]): Unit = {
    val checks = new Checks
    contracts(checks, "production", () => new PluginLoaderDefaultImpl())
    contracts(checks, "dummy", () => new StaticPluginLoader)
    println("SESSION_ENVIRONMENT_CONTRACTS_OK checks=" + checks.count)
    SessionEnvironmentFixturePlatform.concurrent {
      executionContext =>
        concurrent("production", () => new PluginLoaderDefaultImpl(), executionContext)
          .flatMap(_ => concurrent("dummy", () => new StaticPluginLoader, executionContext))(executionContext)
    }
  }

  private def contracts(checks: Checks, label: String, makeLoader: () => PluginLoader): Unit = {
    val provisions = new AtomicInteger(0)
    val definitions = new ModuleDef {
      make[FixtureValue].from {
        () =>
          provisions.incrementAndGet().discard()
          new FixtureValue
      }
    }
    val config = TestConfig.empty.copy(pluginConfig = PluginConfig.constUnchecked(definitions))
    val firstModule = new ModuleDef { make[String].fromValue("first-default") }
    val secondModule = new ModuleDef { make[String].fromValue("second-default") }
    val loader = new RecordingLoader(makeLoader())
    val owner = new SessionTestEnvironment(new TestEnvironmentFactory.Impl)
    checks.verify(label + " construction is deferred")(loader.count.get() == 0 && provisions.get() == 0)

    val first = load(owner, loader, config, firstModule)
    checks.verify(label + " preserves definitions without provisioning") {
      first.appModule.keys == definitions.keys && first.defaultModule.eq(firstModule) && provisions.get() == 0
    }
    val repeated = load(owner, loader, config, firstModule)
    checks.verify(label + " repeats inside one owner")((first eq repeated) && loader.count.get() == 2)

    val secondOwner = new SessionTestEnvironment(new TestEnvironmentFactory.Impl)
    val independent = load(secondOwner, loader, config, firstModule)
    checks.verify(label + " independent owner loads independently")((first ne independent) && loader.count.get() == 4)

    val changedDefault = load(owner, loader, config, secondModule)
    checks.verify(label + " retains a different default for the same effect") {
      changedDefault.defaultModule.eq(secondModule) && first.defaultModule.eq(firstModule) && (first ne changedDefault)
    }

    val replacement = new RecordingLoader(makeLoader())
    val changedLoader = load(owner, replacement, config, firstModule)
    checks.verify(label + " loader identity is part of the request")((first ne changedLoader) && replacement.count.get() == 2)

    val firstContribution = new ModuleDef { make[Long].fromValue(1L) }
    val secondContribution = new ModuleDef { make[Int].fromValue(2) }
    val firstEqualLoader = new EqualLoader(makeLoader(), firstContribution)
    val secondEqualLoader = new EqualLoader(makeLoader(), secondContribution)
    val firstLoaded = load(owner, firstEqualLoader, config, firstModule)
    val secondLoaded = load(owner, secondEqualLoader, config, firstModule)
    val repeatedLoaded = load(owner, secondEqualLoader, config, firstModule)
    checks.verify(label + " distinct equal-valued loaders retain their own definitions") {
      (firstEqualLoader ne secondEqualLoader) && firstEqualLoader == secondEqualLoader &&
      (firstLoaded ne secondLoaded) && (secondLoaded eq repeatedLoaded) &&
      firstLoaded.appModule.keys == (definitions.keys ++ firstContribution.keys) &&
      secondLoaded.appModule.keys == (definitions.keys ++ secondContribution.keys) &&
      firstEqualLoader.count.get() == 2 && secondEqualLoader.count.get() == 2
    }

    val firstStrategy = new EqualStrategy(firstContribution)
    val secondStrategy = new EqualStrategy(secondContribution)
    val firstMerged = owner.load[Identity](config, loader, emptyRoles(), firstStrategy, TagK[Identity], DefaultModule[Identity](firstModule))
    val secondMerged = owner.load[Identity](config, loader, emptyRoles(), secondStrategy, TagK[Identity], DefaultModule[Identity](firstModule))
    val repeatedMerged = owner.load[Identity](config, loader, emptyRoles(), secondStrategy, TagK[Identity], DefaultModule[Identity](firstModule))
    checks.verify(label + " distinct equal-valued merge strategies retain their own definitions") {
      (firstStrategy ne secondStrategy) && firstStrategy == secondStrategy &&
      (firstMerged ne secondMerged) && (secondMerged eq repeatedMerged) &&
      firstMerged.appModule.keys == (definitions.keys ++ firstContribution.keys) &&
      secondMerged.appModule.keys == (definitions.keys ++ secondContribution.keys) &&
      firstStrategy.count.get() == 2 && secondStrategy.count.get() == 2
    }

    val changedConfig = load(owner, loader, config.copy(activation = Activation.empty), firstModule)
    checks.verify(label + " configuration remains distinct") {
      changedConfig.activation == Activation.empty && first.activation == config.activation && (changedConfig ne first)
    }

    val failure = new IllegalStateException(label + " loader failure")
    val failures = new AtomicInteger(0)
    val failing = new PluginLoader {
      private val delegate = makeLoader()
      override def load(config: PluginConfig): LoadedPlugins = {
        if (failures.getAndIncrement() == 0) throw failure
        delegate.load(config)
      }
    }
    val rejected = Try(load(owner, failing, config, firstModule))
    checks.verify(label + " errors propagate without an environment")(rejected.failed.toOption.exists(_ eq failure))
    val retried = load(owner, failing, config, firstModule)
    checks.verify(label + " failed construction is not cached")(retried.defaultModule.eq(firstModule) && failures.get() == 3)

    val order = scala.collection.mutable.ArrayBuffer.empty[String]
    val ordered = new PluginLoader {
      private val delegate = makeLoader()
      override def load(config: PluginConfig): LoadedPlugins = {
        order += "plugins"
        delegate.load(config)
      }
    }
    val created = new TestEnvironmentFactory.Impl().create[Identity](
      config,
      ordered,
      emptyRoles(),
      SimplePluginMergeStrategy,
      TagK[Identity],
      () => { order += "default"; firstModule },
    )
    checks.verify(label + " default evaluation follows successful plugin loads") {
      order.toVector == Vector("plugins", "plugins", "default") && created.defaultModule.eq(firstModule)
    }
    checks.verify(label + " all environment operations leave providers suspended")(provisions.get() == 0)
  }

  private def concurrent(label: String, makeLoader: () => PluginLoader, executionContext: ExecutionContext): Future[Unit] = {
    val loader = new RecordingLoader(makeLoader())
    val firstOwner = new SessionTestEnvironment(new TestEnvironmentFactory.Impl)
    val secondOwner = new SessionTestEnvironment(new TestEnvironmentFactory.Impl)
    val firstModule = new ModuleDef { make[String].fromValue("concurrent-first") }
    val secondModule = new ModuleDef { make[String].fromValue("concurrent-second") }
    val first = Vector.fill(ConcurrentRequests)(Future(load(firstOwner, loader, TestConfig.empty, firstModule))(executionContext))
    val second = Vector.fill(ConcurrentRequests)(Future(load(secondOwner, loader, TestConfig.empty, secondModule))(executionContext))
    implicit val ec: ExecutionContext = executionContext
    Future.sequence(first ++ second).map {
      environments =>
        val left = environments.take(ConcurrentRequests)
        val right = environments.drop(ConcurrentRequests)
        require(left.forall(_.eq(left.head)) && right.forall(_.eq(right.head)), label + " concurrently duplicated a cached environment")
        require((left.head ne right.head) && left.head.defaultModule.eq(firstModule) && right.head.defaultModule.eq(secondModule), label + " crossed owner boundaries")
        require(loader.count.get() == 4, label + " concurrent requests reloaded plugins")
        println("SESSION_ENVIRONMENT_CONCURRENT_OK adapter=" + label + " requests=" + (ConcurrentRequests * 2))
    }
  }

  private def load(owner: SessionTestEnvironment, loader: PluginLoader, config: TestConfig, module: Module): TestEnvironment = {
    owner.load[Identity](config, loader, emptyRoles(), SimplePluginMergeStrategy, TagK[Identity], DefaultModule[Identity](module))
  }

  private def emptyRoles(): RolesInfo = RolesInfo(Set.empty, Set.empty, Set.empty, Set.empty, Set.empty, Set.empty)

  private final class FixtureValue

  private final class StaticPluginLoader extends PluginLoader {
    override def load(config: PluginConfig): LoadedPlugins = {
      require(config.packagesEnabled.isEmpty && config.packagesDisabled.isEmpty, "Static fixture loader requires explicit modules")
      LoadedPlugins(Nil, config.merges, config.overrides)
    }
  }

  private final class RecordingLoader(delegate: PluginLoader) extends PluginLoader {
    val count = new AtomicInteger(0)
    override def load(config: PluginConfig): LoadedPlugins = {
      count.incrementAndGet().discard()
      delegate.load(config)
    }
  }

  private final class EqualLoader(delegate: PluginLoader, contribution: Module) extends PluginLoader {
    val count = new AtomicInteger(0)
    override def load(config: PluginConfig): LoadedPlugins = {
      count.incrementAndGet().discard()
      delegate.load(config) ++ LoadedPlugins.const(Seq(contribution))
    }
    override def equals(other: Any): Boolean = other.isInstanceOf[EqualLoader]
    override def hashCode(): Int = 1
  }

  private final class EqualStrategy(contribution: Module) extends PluginMergeStrategy {
    val count = new AtomicInteger(0)
    override def merge(definitions: Seq[ModuleBase]): ModuleBase = {
      count.incrementAndGet().discard()
      SimplePluginMergeStrategy.merge(definitions :+ contribution)
    }
    override def equals(other: Any): Boolean = other.isInstanceOf[EqualStrategy]
    override def hashCode(): Int = 1
  }

  private final class Checks {
    private var verified = 0
    def count: Int = verified
    def verify(label: String)(condition: => Boolean): Unit = {
      require(condition, label)
      verified += 1
      println("SESSION_ENVIRONMENT_CHECK " + label)
    }
  }
}
