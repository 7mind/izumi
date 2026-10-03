package izumi.distage.testkit.spec

import distage.Injector
import izumi.distage.model.definition.{Activation, Module, ModuleBase, ModuleDef}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoader, PluginLoaderDefaultImpl, PluginPackageCache}
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
    pluginContracts(checks, "production", cache => PluginLoaderDefaultImpl.withPackageCache(cache))
    pluginContracts(checks, "dummy", _ => new StaticPluginLoader)
    val wiring = new ModuleDef { make[PluginLoader].from[PluginLoaderDefaultImpl] }
    checks.verify("default plugin loader retains ordinary DI construction") {
      Injector().produceRun(wiring)((loader: PluginLoader) => loader.isInstanceOf[PluginLoaderDefaultImpl])
    }
    customLoaderPolicies(checks)
    packageKeySnapshots(checks)
    requestKeySnapshots(checks)
    pluginConfigurationOwnership(checks)
    environmentKeySnapshots(checks)
    PreparedExecutionFixtures.checks().foreach {
      case (label, condition) => checks.verify(label)(condition)
    }
    SessionEnvironmentFixturePlatform.runnerCompletionChecks().foreach {
      case (label, condition) => checks.verify(label)(condition)
    }
    SessionEnvironmentFixturePlatform.scannedOwners().foreach {
      case (label, condition) => checks.verify(label)(condition)
    }
    println("SESSION_ENVIRONMENT_CONTRACTS_OK checks=" + checks.count)
    SessionEnvironmentFixturePlatform.concurrent {
      executionContext =>
        concurrent("production", () => new PluginLoaderDefaultImpl(), executionContext)
          .flatMap(_ => concurrent("dummy", () => new StaticPluginLoader, executionContext))(executionContext)
          .flatMap(_ => concurrentPlugins("production", cache => PluginLoaderDefaultImpl.withPackageCache(cache), executionContext))(executionContext)
          .flatMap(_ => concurrentPlugins("dummy", _ => new StaticPluginLoader, executionContext))(executionContext)
    }
  }

  private def pluginConfigurationOwnership(checks: Checks): Unit = {
    val first = new PluginPackageCache.Impl
    val second = new PluginPackageCache.Impl
    val original = PluginConfig.cached(Seq("original"))
    val owned = original.withPackageCacheOwner(first)
    checks.verify("owned plugin configuration retains six-field equality and product shape") {
      owned == original && owned.hashCode() == original.hashCode() && owned.productArity == 6 && owned.productIterator.toVector == original.productIterator.toVector && owned.productPrefix == original.productPrefix
    }
    checks.verify("owned plugin configuration retains six-field construction and extraction") {
      val PluginConfig(enabled, disabled, cached, debug, merges, overrides) = owned
      new PluginConfig(enabled, disabled, cached, debug, merges, overrides) == original
    }
    val transformed = Vector(
      owned.copy(debug = true), owned.snapshot(), owned.enablePackage("enabled"), owned.disablePackage("disabled"),
      owned ++ Module.empty, owned.overriddenBy(Module.empty), owned.cachePackages(false), owned.debug(true),
    )
    checks.verify("plugin copy, snapshot and all request helpers preserve their owner") {
      transformed.forall(config => config.packageCacheOwner.exists(_ eq first))
    }
    checks.verify("plugin configuration can enter another owner without changing its original scope") {
      owned.withPackageCacheOwner(second).packageCacheOwner.exists(_ eq second) && owned.packageCacheOwner.exists(_ eq first) && original.packageCacheOwner.isEmpty
    }
    val packages = scala.collection.mutable.ArrayBuffer("snapshot")
    val snapshot = owned.copy(packagesEnabled = packages.toSeq).snapshot()
    (packages += "changed").discard()
    checks.verify("owned plugin snapshot freezes mutable packages and retains its cache") {
      snapshot.packagesEnabled == Vector("snapshot") && snapshot.packageCacheOwner.exists(_ eq first)
    }
  }

  private def environmentKeySnapshots(checks: Checks): Unit = {
    val config = PluginConfig.empty.cachePackages(true)
    checkEnvironmentSnapshot(checks, "enabled", scala.collection.mutable.ArrayBuffer("original"), (values: Seq[String]) => config.copy(packagesEnabled = values))
    checkEnvironmentSnapshot(checks, "disabled", scala.collection.mutable.ArrayBuffer("original"), (values: Seq[String]) => config.copy(packagesDisabled = values))
    checkEnvironmentSnapshot(checks, "merges", scala.collection.mutable.ArrayBuffer(Module.empty), (values: Seq[Module]) => config.copy(merges = values))
    checkEnvironmentSnapshot(checks, "overrides", scala.collection.mutable.ArrayBuffer(Module.empty), (values: Seq[Module]) => config.copy(overrides = values))
  }

  private def checkEnvironmentSnapshot[A](
    checks: Checks,
    label: String,
    input: scala.collection.mutable.ArrayBuffer[A],
    makeConfig: Seq[A] => PluginConfig,
  ): Unit = {
    Vector(false, true).foreach {
      bootstrap =>
        val values = input.clone()
        val request = makeConfig(values.toSeq)
        val original = makeConfig(values.toVector)
        val initial = if (bootstrap) TestConfig.empty.copy(bootstrapPluginConfig = request) else TestConfig.empty.copy(pluginConfig = request)
        val repeatedConfig = if (bootstrap) TestConfig.empty.copy(bootstrapPluginConfig = original) else TestConfig.empty.copy(pluginConfig = original)
        val owner = new SessionTestEnvironment(new TestEnvironmentFactory.Impl)
        val loader = new RecordingLoader(new PluginLoader {
          override def load(config: PluginConfig): LoadedPlugins = LoadedPlugins(Nil, config.merges, config.overrides)
        })
        val first = load(owner, loader, initial, Module.empty)
        values.clear()
        val repeated = load(owner, loader, repeatedConfig, Module.empty)
        checks.verify("environment snapshots plugin sequences field=" + label + " bootstrap=" + bootstrap) {
          (first eq repeated) && loader.count.get() == 2
        }
    }
  }

  private def requestKeySnapshots(checks: Checks): Unit = {
    val config = PluginConfig.empty.cachePackages(true)
    checkRequestSnapshot(checks, "enabled", scala.collection.mutable.ArrayBuffer("original"), (values: Seq[String]) => config.copy(packagesEnabled = values))
    checkRequestSnapshot(checks, "disabled", scala.collection.mutable.ArrayBuffer("original"), (values: Seq[String]) => config.copy(packagesDisabled = values))
    checkRequestSnapshot(checks, "merges", scala.collection.mutable.ArrayBuffer(Module.empty), (values: Seq[Module]) => config.copy(merges = values))
    checkRequestSnapshot(checks, "overrides", scala.collection.mutable.ArrayBuffer(Module.empty), (values: Seq[Module]) => config.copy(overrides = values))
  }

  private def checkRequestSnapshot[A](
    checks: Checks,
    label: String,
    input: scala.collection.mutable.ArrayBuffer[A],
    makeConfig: Seq[A] => PluginConfig,
  ): Unit = {
    val original = makeConfig(input.toVector)
    val request = makeConfig(input.toSeq)
    val loads = new AtomicInteger(0)
    val owner = new SessionPluginLoader(_ => new PluginLoader {
      override def load(config: PluginConfig): LoadedPlugins = {
        loads.incrementAndGet().discard()
        LoadedPlugins(Nil, config.merges, config.overrides)
      }
    })
    val first = owner.load(request)
    input.clear()
    val repeated = owner.load(original)
    checks.verify("complete plugin requests snapshot caller sequences field=" + label) {
      loads.get() == 1 && (first eq repeated) && first.merges == original.merges && first.overrides == original.overrides
    }
  }

  private def packageKeySnapshots(checks: Checks): Unit = {
    Vector(false, true).foreach {
      whitelist =>
        val cache = new PluginPackageCache.Impl
        val input = scala.collection.mutable.ArrayBuffer("original")
        val supplied = input.toSeq
        val loads = new AtomicInteger(0)
        def load: Seq[izumi.distage.plugins.PluginBase] = {
          loads.incrementAndGet().discard()
          Nil
        }
        val empty = Vector.empty[String]
        if (whitelist) cache.getOrCompute("fixture-package", supplied, empty)(load).discard()
        else cache.getOrCompute("fixture-package", empty, supplied)(load).discard()
        input(0) = "changed"
        if (whitelist) cache.getOrCompute("fixture-package", Vector("original"), empty)(load).discard()
        else cache.getOrCompute("fixture-package", empty, Vector("original"))(load).discard()
        checks.verify("package cache snapshots its caller sequences whitelist=" + whitelist)(loads.get() == 1)
    }
  }

  private def customLoaderPolicies(checks: Checks): Unit = {
    Vector(false, true).foreach {
      mapped =>
        val failure = new IllegalStateException("custom plugin loading policy")
        val calls = new AtomicInteger(0)
        val owner = new SessionPluginLoader(_ => {
          val loader = new PluginLoader {
            override def load(config: PluginConfig): LoadedPlugins = {
              calls.incrementAndGet().discard()
              throw failure
            }
          }
          if (mapped) loader.map(loaded => loaded) else loader
        })
        val result = Try(owner.load(PluginConfig.empty.cachePackages(true)))
        checks.verify("custom loading policy survives session ownership mapped=" + mapped) {
          calls.get() == 1 && result.failed.toOption.exists(_ eq failure)
        }
    }
  }

  private def concurrentPlugins(label: String, makeLoader: PluginPackageCache => PluginLoader, executionContext: ExecutionContext): Future[Unit] = {
    val provisions = new AtomicInteger(0)
    val definitions = new ModuleDef {
      make[FixtureValue].from {
        () =>
          provisions.incrementAndGet().discard()
          new FixtureValue
      }
    }
    val config = PluginConfig.constUnchecked(definitions).cachePackages(true)
    val loads = new AtomicInteger(0)
    val create = (cache: PluginPackageCache) => new PluginLoader {
      private val delegate = makeLoader(cache)
      override def load(config: PluginConfig): LoadedPlugins = {
        loads.incrementAndGet().discard()
        delegate.load(config)
      }
    }
    val firstOwner = new SessionPluginLoader(create)
    val secondOwner = new SessionPluginLoader(create)
    val first = Vector.fill(ConcurrentRequests)(Future(firstOwner.load(config))(executionContext))
    val second = Vector.fill(ConcurrentRequests)(Future(secondOwner.load(config))(executionContext))
    implicit val ec: ExecutionContext = executionContext
    Future.sequence(first ++ second).map {
      loaded =>
        val left = loaded.take(ConcurrentRequests)
        val right = loaded.drop(ConcurrentRequests)
        require(left.forall(_.eq(left.head)) && right.forall(_.eq(right.head)), label + " concurrently duplicated a cached plugin request")
        require((left.head ne right.head) && loads.get() == 2, label + " plugin request crossed owner boundaries or reloaded")
        require(provisions.get() == 0, label + " plugin loading executed a bound provider")
        println("SESSION_PLUGIN_CONCURRENT_OK adapter=" + label + " requests=" + (ConcurrentRequests * 2))
    }
  }

  private def pluginContracts(checks: Checks, label: String, makeLoader: PluginPackageCache => PluginLoader): Unit = {
    val provisions = new AtomicInteger(0)
    val definitions = new ModuleDef {
      make[FixtureValue].from {
        () =>
          provisions.incrementAndGet().discard()
          new FixtureValue
      }
    }
    val config = PluginConfig.constUnchecked(definitions).cachePackages(true)
    val requests = scala.collection.mutable.ArrayBuffer.empty[PluginConfig]
    val create = (cache: PluginPackageCache) => new PluginLoader {
      private val loader = makeLoader(cache)
      override def load(config: PluginConfig): LoadedPlugins = {
        requests += config
        loader.load(config)
      }
    }
    val owner = new SessionPluginLoader(create)
    checks.verify(label + " plugin cache construction is deferred")(requests.isEmpty && provisions.get() == 0)
    val first = owner.load(config)
    val repeated = owner.load(config)
    checks.verify(label + " plugin cache repeats within one owner") {
      (first eq repeated) && requests.size == 1 && first.merges == config.merges && first.overrides == config.overrides
    }
    val independent = new SessionPluginLoader(create).load(config)
    checks.verify(label + " plugin cache loads independently for another owner")((first ne independent) && requests.size == 2)

    val uncachedFirst = owner.load(config.cachePackages(false))
    val uncachedSecond = owner.load(config.cachePackages(false))
    checks.verify(label + " uncached plugin requests always delegate")((uncachedFirst ne uncachedSecond) && requests.size == 4)
    val overrideModule = new ModuleDef { make[String].fromValue("plugin-request-override") }
    val changed = config.overriddenBy(Seq(overrideModule))
    val changedResult = owner.load(changed)
    checks.verify(label + " plugin requests preserve different definitions") {
      requests.size == 5 && changedResult.overrides == Seq(overrideModule) && first.overrides.isEmpty
    }
    checks.verify(label + " plugin cache preserves complete requests without provisioning") {
      requests.toVector == Vector(config, config, config.cachePackages(false), config.cachePackages(false), changed) && provisions.get() == 0
    }

    val failure = new IllegalStateException(label + " cached plugin failure")
    val attempts = new AtomicInteger(0)
    val failing = new SessionPluginLoader(cache => new PluginLoader {
      private val loader = makeLoader(cache)
      override def load(config: PluginConfig): LoadedPlugins = {
        if (attempts.getAndIncrement() == 0) throw failure
        loader.load(config)
      }
    })
    val rejected = Try(failing.load(config))
    checks.verify(label + " plugin cache retains the original failure")(rejected.failed.toOption.exists(_ eq failure))
    val retried = failing.load(config)
    checks.verify(label + " failed plugin loading is not cached")((retried eq failing.load(config)) && attempts.get() == 2 && provisions.get() == 0)
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
