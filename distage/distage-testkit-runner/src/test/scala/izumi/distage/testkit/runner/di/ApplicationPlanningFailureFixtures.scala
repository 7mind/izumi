package izumi.distage.testkit.runner.di

import distage.{Activation, DIKey, ModuleDef}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.{ConfigLoader, ModuleProvider}
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.impl.services.BootstrapFactory
import izumi.distage.testkit.runner.spec.SpecIdentity
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.reflect.TagK

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

sealed trait PlanningFailureStage { def name: String }
object PlanningFailureStage {
  case object Configuration extends PlanningFailureStage { override val name: String = "configuration" }
  case object Extension extends PlanningFailureStage { override val name: String = "extension" }
}

abstract class PlanningFixtureBootstrapBase(stage: PlanningFailureStage, configs: AtomicInteger, extensions: AtomicInteger, original: Throwable) extends BootstrapFactory {
  override def makeConfigLoader(configBaseName: String, logger: IzLogger): ConfigLoader = {
    val _ = (configBaseName, logger)
    new ConfigLoader {
      override def loadConfig(clue: String): AppConfig = {
        val _ = (clue, configs.incrementAndGet())
        if (stage == PlanningFailureStage.Configuration) throw original
        AppConfig.empty
      }
    }
  }

  override def makeModuleProvider[F[_]: TagK](options: PlanningOptions, config: AppConfig, logRouter: LogRouter, roles: RolesInfo, activationInfo: ActivationInfo, activation: Activation): ModuleProvider = {
    val _ = extensions.incrementAndGet()
    if (stage == PlanningFailureStage.Extension) throw original
    BootstrapFactory.Impl.makeModuleProvider[F](options, config, logRouter, roles, activationInfo, activation)
  }
}

object ApplicationPlanningFailureFixtures {
  private final class Resource

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    Vector(PlanningFailureStage.Configuration, PlanningFailureStage.Extension).foldLeft(Future.unit) { (previous, stage) => previous.flatMap { _ =>
      val configs = new AtomicInteger(0)
      val extensions = new AtomicInteger(0)
      val acquired = new AtomicInteger(0)
      val released = new AtomicInteger(0)
      val bodies = new AtomicInteger(0)
      val original = new IllegalStateException("Controlled application " + stage.name + " failure")
      val definitions = new ModuleDef {
        make[Resource].fromResource(() => Lifecycle.make[Identity, Resource] { val _ = acquired.incrementAndGet(); new Resource } { _ => val _ = released.incrementAndGet(); () })
      }
      val suite = new SpecIdentity {
        override protected def config: TestConfig = TestConfig.empty.copy(
          pluginConfig = PluginConfig.constUnchecked(definitions),
          activation = Activation.empty,
          activationStrategy = TestActivationStrategy.IgnoreConfig,
          bootstrapFactory = new PlanningFixtureBootstrap(stage, configs, extensions, original),
          memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])),
        )
        "planning failure" in { (_: Resource) => val _ = bodies.incrementAndGet(); () }
      }
      val identity = CatalogueIdentity(BuildId("application-planning-failure"), BuildTargetId("planning-target"), CatalogueId(stage.name))
      val run = RunId(stage.name)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      var messages = Vector.empty[ProtocolMessage]
      val output = new ProtocolOutput {
        override def accept(message: ProtocolMessage): Unit = synchronized {
          require(ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), "Planning failure output must round-trip")
          messages :+= message
        }
      }
      val app = new TestApplication(run, identity, Vector(() => suite), context, output)
      app.accept(ProtocolMessage.Discover(run, identity.build, identity.target)).flatMap { _ =>
        verify(stage.name + " discovery performs no configuration read, module-provider creation or user acquisition", configs.get() == 0 && extensions.get() == 0 && acquired.get() == 0 && bodies.get() == 0)
        verify(stage.name + " discovery has a correlated catalogue", output.synchronized(messages).count { case ProtocolMessage.Discovered(`run`, _) => true; case _ => false } == 1)
        Vector(RequestOperation.Resolve, RequestOperation.Plan, RequestOperation.Execute).foldLeft(Future.unit) { (before, operation) => before.flatMap(_ => app.accept(ProtocolMessage.Request(operation, run, request))) }
      }.map { _ =>
        val snapshot = output.synchronized(messages)
        val rejections = snapshot.collect { case ProtocolMessage.Rejected(`run`, failure) => failure }
        val inspected = snapshot.collect { case ProtocolMessage.Planned(`run`, plan) => plan }.flatMap(_.inspection.failures)
        val outcomes = snapshot.collect { case ProtocolMessage.Completed(outcome) => outcome }
        val executionFailures = outcomes.flatMap(_.results.flatMap(_.failure))
        val retained = if (stage == PlanningFailureStage.Configuration) rejections else inspected.map(_.failure) ++ executionFailures
        def originalFailure(failure: Failure): Boolean = (failure.exceptionClass == original.getClass.getName && failure.message == original.getMessage) || failure.causes.exists(originalFailure)
        val expectedFailures = if (stage == PlanningFailureStage.Configuration) 3 else 2
        verify(stage.name + " failure retains its planning phase and original diagnostic", retained.size == expectedFailures && retained.forall(failure => failure.phase == FailurePhase.Planning && originalFailure(failure)))
        verify(stage.name + " plan and execution retain the failed snapshot", retained.distinct.size == 1)
        verify(stage.name + " configuration read and module-provider calls occur once at their measured phases", configs.get() == 1 && extensions.get() == (if (stage == PlanningFailureStage.Extension) 1 else 0))
        verify(stage.name + " failure executes no user resource or body", acquired.get() == 0 && released.get() == 0 && bodies.get() == 0)
        verify(stage.name + " failures use explicit rejection or inspected/executed planning failure", if (stage == PlanningFailureStage.Configuration) !snapshot.exists { case _: ProtocolMessage.Event | _: ProtocolMessage.Completed => true; case _ => false } else rejections.isEmpty && outcomes.size == 1 && !outcomes.head.successful && inspected.map(_.tests) == outcomes.map(_.results.map(_.id)))
        verify(stage.name + " resolution succeeds only before the planning extension", snapshot.exists(_.isInstanceOf[ProtocolMessage.Resolved]) == (stage == PlanningFailureStage.Extension))
        if (stage == PlanningFailureStage.Extension) {
          val selectedIds = snapshot.collect { case ProtocolMessage.Discovered(`run`, catalogue) => catalogue.tests.map(_.id) }.head
          val completed = outcomes.head
          verify("extension selected tests fail with no cancellation or run-level failures", selectedIds.nonEmpty && completed.results.map(_.id) == selectedIds && completed.results.forall(_.status == TestStatus.Failed) && !completed.cancelled && completed.failures.isEmpty)
          val completedEvents = snapshot.collect { case ProtocolMessage.Event(_, RunEvent.TestCompleted(`run`, result)) => result }
          val finishedEvents = snapshot.collect { case ProtocolMessage.Event(_, RunEvent.Finished(`run`, outcome)) => outcome }
          verify("extension terminal events reconcile with the retained execution outcome", completedEvents == completed.results && finishedEvents == Vector(completed) && snapshot.last == ProtocolMessage.Completed(completed) && snapshot.collect { case ProtocolMessage.Event(sequence, _) => sequence } == snapshot.collect { case event: ProtocolMessage.Event => event }.indices.map(_.toLong).toVector)
        }
        println("APPLICATION_PLANNING_FAILURE_OK kind=" + stage.name + " configReads=once moduleProvider=phase-specific resources=untouched phase=planning failures=retained")
      }
    } }
  }
}
