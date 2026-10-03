package izumi.distage.testkit.runner.di

import cats.effect.IO
import distage.DIKey
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoader, PluginLoaderDefaultImpl, PluginLoaderFactory, PluginPackageCache}
import izumi.distage.testkit.model.{SuiteId as EngineSuiteId, TestActivationStrategy, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.di.sessionplugins.SessionPluginResource
import izumi.distage.testkit.runner.spec.Spec1
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.*
import scala.util.control.NonFatal

private[di] object SpecPluginOwnershipFixtures {
  private final val ScannedPackage = "izumi.distage.testkit.runner.di.sessionplugins"
  private final val WorkerTimeout = 30.seconds

  private final class Statistics {
    val configurations = new AtomicInteger(0)
    val loaders = new AtomicInteger(0)
    private var requests = Vector.empty[PluginConfig]
    private var resources = Vector.empty[SessionPluginResource]
    private var bodyCounts = Vector.empty[Int]
    def request(config: PluginConfig): Unit = synchronized { requests :+= config }
    def body(resource: SessionPluginResource): Boolean = synchronized {
      resources :+= resource
      bodyCounts :+= resource.state.bodies.incrementAndGet()
      resources.size == 2
    }
    def loaded: Vector[PluginConfig] = synchronized { requests }
    def seen: Vector[SessionPluginResource] = synchronized { resources }
    def counts: Vector[Int] = synchronized { bodyCounts }
  }

  private final class OwnerGate(expected: Int, label: String, verify: (String, Boolean) => Unit) {
    private val open = Promise[Unit]()
    private var entered = Vector.empty[Statistics]
    def enter(stats: Statistics): Future[Unit] = synchronized {
      try {
        require(!entered.contains(stats), "Custom plugin owner entered its resource gate twice")
        entered :+= stats
        require(entered.size <= expected, "Custom plugin resource gate admitted too many owners")
        if (entered.size == expected) {
          println("DISTAGE_SPEC_CUSTOM_PLUGIN_GATE label=" + label + " counts=" + entered.map(_.counts.mkString(",")).mkString(";") + " acquired=" + entered.map(_.seen.head.state.acquired.get()).mkString(",") + " released=" + entered.map(_.seen.head.state.released.get()).mkString(","))
          verify(label + " custom plugins hold every owner's resource before opening the gate", entered.forall { owner =>
            val state = owner.seen.head.state
            owner.counts == Vector(1, 2) && state.acquired.get() == 1 && state.released.get() == 0
          })
          open.success(()).discard()
        }
        open.future
      } catch {
        case NonFatal(cause) => open.tryFailure(cause).discard(); throw cause
      }
    }
  }

  private final class Suite(debug: Boolean, fresh: Boolean, reconstruct: Boolean, worker: Option[ExecutionContext], stats: Statistics, gate: OwnerGate) extends Spec1[IO] {
    override protected def distageSuiteId: EngineSuiteId = EngineSuiteId("custom-plugin-" + debug)
    override protected def config: TestConfig = {
      stats.configurations.incrementAndGet().discard()
      TestConfig.empty.copy(
        pluginConfig = PluginConfig.empty.enablePackage(ScannedPackage).cachePackages(true),
        memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[SessionPluginResource])),
        activationStrategy = TestActivationStrategy.IgnoreConfig,
        parallelEnvs = TestConfig.Parallelism.Sequential,
        parallelSuites = TestConfig.Parallelism.Sequential,
        parallelTests = TestConfig.Parallelism.Sequential,
        debugOutput = debug,
      )
    }
    override protected def makePluginLoaderFactory(): PluginLoaderFactory = {
      stats.loaders.incrementAndGet().discard()
      if (fresh) new PluginLoaderFactory {
        override def create(owner: PluginPackageCache): PluginLoader = {
          if (reconstruct) {
            val delegate = new PluginLoaderDefaultImpl {
              override protected val packageCache: PluginPackageCache = owner
              private def onWorker(config: PluginConfig): LoadedPlugins = super.load(config)
              override def load(config: PluginConfig): LoadedPlugins = {
                stats.request(config)
                val request = PluginConfig(config.packagesEnabled, config.packagesDisabled, config.cachePackages, debug, config.merges, config.overrides)
                worker match {
                  case Some(context) => Await.result(Future(onWorker(request))(context), WorkerTimeout)
                  case None => super.load(request)
                }
              }
            }
            if (debug) delegate.map(value => value) else delegate
          } else {
            val delegate = PluginLoaderDefaultImpl.withPackageCache(owner).map(value => value)
            new PluginLoader {
              override def load(config: PluginConfig): LoadedPlugins = {
                stats.request(config)
                delegate.load(config.snapshot().copy(debug = debug))
              }
            }
          }
        }
      } else super.makePluginLoaderFactory()
    }
    "resource" in { (resource: SessionPluginResource) =>
      IO(stats.body(resource)).flatMap(last => if (last) IO.fromFuture(IO(gate.enter(stats))) else IO.unit)
    }
  }

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    runOwners("", reconstruct = false, None, context, verify)
      .flatMap(_ => runOwners("reconstructed-", reconstruct = true, None, context, verify))
      .flatMap(_ => handoff(context, verify))
  }

  private def handoff(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val executor = Executors.newSingleThreadExecutor()
    val worker: ExecutionContext = ExecutionContext.fromExecutorService(executor)
    Future(())(worker).flatMap(_ => runOwners("handoff-", reconstruct = true, Some(worker), context, verify)).transform { result =>
      executor.shutdown()
      require(executor.awaitTermination(WorkerTimeout.toMillis, TimeUnit.MILLISECONDS), "Plugin handoff worker must terminate")
      println("DISTAGE_SPEC_PLUGIN_HANDOFF_EXECUTOR_TERMINATED")
      result
    }
  }

  private def runOwners(prefix: String, reconstruct: Boolean, worker: Option[ExecutionContext], context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val custom = Vector(true, true)
    exercise(prefix + "first", custom, reconstruct, worker, new OwnerGate(1, prefix + "first", verify), context, verify)
      .flatMap { first => exercise(prefix + "repeated", custom, reconstruct, worker, new OwnerGate(1, prefix + "repeated", verify), context, verify).map(second => verify(prefix + "custom plugins keep fresh state across repeated owners", first.state ne second.state)) }
      .flatMap(_ => exercise(prefix + "mixed", Vector(false, true), reconstruct, worker, new OwnerGate(1, prefix + "mixed", verify), context, verify))
      .flatMap { _ =>
        val gate = new OwnerGate(2, prefix + "concurrent", verify)
        Future.sequence(Vector(prefix + "concurrent-first", prefix + "concurrent-second").map(owner => exercise(owner, custom, reconstruct, worker, gate, context, verify)))
          .map(resources => verify(prefix + "custom plugins keep distinct resources and state across concurrent owners", (resources.head ne resources(1)) && (resources.head.state ne resources(1).state)))
      }
  }

  private def exercise(owner: String, custom: Vector[Boolean], reconstruct: Boolean, worker: Option[ExecutionContext], gate: OwnerGate, context: ExecutionContext, verify: (String, Boolean) => Unit): Future[SessionPluginResource] = {
    implicit val ec: ExecutionContext = context
    val stats = new Statistics
    val identity = CatalogueIdentity(BuildId("custom-plugin"), BuildTargetId("plugin-target"), CatalogueId(owner))
    var events = Vector.empty[ProtocolMessage.Event]
    var releasesAtFinish = Vector.empty[Int]
    val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = synchronized {
      events :+= event
      event.event match {
        case _: RunEvent.Finished => releasesAtFinish :+= stats.seen.head.state.released.get()
        case _ => ()
      }
    } }
    val factories = Vector(false, true).zip(custom).map { case (debug, fresh) => () => new Suite(debug, fresh, reconstruct, worker, stats, gate) }
    val session = new RunSession(identity, factories, context, sink)
    val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify(owner + " custom plugin discovery suspends hooks, resources and bodies", catalogue.tests.size == 2 && stats.configurations.get() == 0 && stats.loaders.get() == 0 && stats.loaded.isEmpty && stats.seen.isEmpty && events.isEmpty)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
    val forwarded = custom.count(value => value)
    verify(owner + " custom plugin forwarding receives both cached app and bootstrap requests", stats.configurations.get() == 2 && stats.loaders.get() == 2 && stats.loaded.size == forwarded * 2 && stats.loaded.count(config => config.packagesEnabled == Seq(ScannedPackage) && config.cachePackages) == forwarded && stats.seen.isEmpty)
    session.plan(resolved).flatMap { result =>
      val planned = result.fold(failure => throw new IllegalStateException(failure.message), value => value)
      verify(owner + " custom plugin planning leaves application resources suspended", stats.seen.isEmpty && events.isEmpty)
      session.execute(RunId(owner), planned).map { outcome =>
        if (!outcome.successful) println("DISTAGE_SPEC_CUSTOM_PLUGIN_OUTCOME " + outcome)
        verify(owner + " custom plugin suites complete both selected bodies", outcome.successful && outcome.results.map(_.id).toSet == catalogue.tests.map(_.id).toSet && stats.seen.size == 2)
        val resources = stats.seen
        val state = resources.head.state
        println("DISTAGE_SPEC_CUSTOM_PLUGIN owner=" + owner + " results=" + outcome.results.size + " counts=" + stats.counts.mkString(",") + " shared=" + (resources.head eq resources(1)) + " acquired=" + state.acquired.get() + " released=" + state.released.get())
        verify(owner + " custom plugin owner starts with fresh scanned state", stats.counts == Vector(1, 2))
        verify(owner + " custom plugin copy and map hooks retain same-owner memoization", (resources.head eq resources(1)) && state.acquired.get() == 1 && state.released.get() == 1)
        verify(owner + " custom plugin resource releases before Finished", releasesAtFinish == Vector(1) && events.last.event == RunEvent.Finished(outcome.run, outcome))
        resources.head
      }
    }
  }
}
