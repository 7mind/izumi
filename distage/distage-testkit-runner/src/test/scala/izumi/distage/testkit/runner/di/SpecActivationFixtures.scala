package izumi.distage.testkit.runner.di

import distage.{Activation, Axis, DIKey, ModuleDef}
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.SpecIdentity
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

private[di] object SpecActivationFixtures {
  private object Mode extends Axis {
    case object Suite extends AxisChoiceDef
    case object Config extends AxisChoiceDef
    case object Explicit extends AxisChoiceDef
  }
  private final class Resource
  private final case class Pair(first: Resource, second: Resource)
  private final case class Chosen(value: String)
  private final case class Request(name: String, suiteChoice: Boolean, overrides: RunOverrides, unknownId: Boolean, expected: Option[String], resources: Int)

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    def choice(value: Axis.AxisChoice): AxisChoice = AxisChoice(AxisId(Mode.name), AxisValue(value.value))
    val requests = Vector(
      Request("loaded-config", false, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit), false, Some(Mode.Config.value), 1),
      Request("suite-precedence", true, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit), false, Some(Mode.Suite.value), 1),
      Request("explicit-precedence", true, RunOverrides(Vector(choice(Mode.Explicit)), Vector.empty, MemoizationOverride.Inherit), false, Some(Mode.Explicit.value), 1),
      Request("effective-filter", true, RunOverrides(Vector(choice(Mode.Explicit)), Vector(choice(Mode.Explicit)), MemoizationOverride.Inherit), false, Some(Mode.Explicit.value), 1),
      Request("memoization-disabled", true, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Disabled), false, Some(Mode.Suite.value), 2),
      Request("unknown-value", true, RunOverrides(Vector(AxisChoice(AxisId(Mode.name), AxisValue("unknown"))), Vector.empty, MemoizationOverride.Inherit), false, None, 0),
      Request("unknown-axis", true, RunOverrides(Vector(AxisChoice(AxisId("unknown"), AxisValue("unknown"))), Vector.empty, MemoizationOverride.Inherit), false, None, 0),
      Request("unknown-filter", true, RunOverrides(Vector.empty, Vector(AxisChoice(AxisId(Mode.name), AxisValue("unknown"))), MemoizationOverride.Inherit), false, None, 0),
      Request("empty-filter", true, RunOverrides(Vector.empty, Vector(choice(Mode.Config)), MemoizationOverride.Inherit), false, None, 0),
      Request("unknown-id", true, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit), true, None, 0),
    )
    requests.foldLeft(Future.successful(())) { (before, request) => before.flatMap { _ =>
      val configurations = new AtomicInteger(0)
      val acquired = new AtomicInteger(0)
      val released = new AtomicInteger(0)
      val bodies = new AtomicInteger(0)
      val shared = new AtomicInteger(0)
      val wrongChoice = new AtomicInteger(0)
      val definitions = new ModuleDef {
        make[Chosen].tagged(Mode.Suite).fromValue(Chosen(Mode.Suite.value))
        make[Chosen].tagged(Mode.Config).fromValue(Chosen(Mode.Config.value))
        make[Chosen].tagged(Mode.Explicit).fromValue(Chosen(Mode.Explicit.value))
        make[Resource].fromResource(() => Lifecycle.make[Identity, Resource] { acquired.incrementAndGet().discard(); new Resource } { _ => released.incrementAndGet().discard() })
        make[Pair].from((first: Resource, second: Resource) => Pair(first, second))
      }
      val suite = new SpecIdentity {
        override protected def config: TestConfig = {
          configurations.incrementAndGet().discard()
          TestConfig.empty.copy(
            pluginConfig = PluginConfig.constUnchecked(definitions),
            activation = if (request.suiteChoice) Activation(Mode -> Mode.Suite) else Activation.empty,
            activationStrategy = TestActivationStrategy.LoadConfig(ignoreUnknown = false, warnUnset = false),
            configOverrides = Some(ProviderFixturePlatform.activationConfig(choice(Mode.Config))),
            memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])),
          )
        }
        private def body(chosen: Chosen, resource: Resource, pair: Pair): Unit = {
          bodies.incrementAndGet().discard()
          if (!request.expected.contains(chosen.value)) wrongChoice.incrementAndGet().discard()
          if ((resource eq pair.first) && (resource eq pair.second)) shared.incrementAndGet().discard()
        }
        "activation" should {
          "first" in { (chosen: Chosen, resource: Resource, pair: Pair) => body(chosen, resource, pair) }
          "second" in { (chosen: Chosen, resource: Resource, pair: Pair) => body(chosen, resource, pair) }
        }
      }
      val identity = CatalogueIdentity(BuildId("spec-activation"), BuildTargetId("spec-target"), CatalogueId(request.name))
      var messages = Vector.empty[ProtocolMessage]
      val output = new ProtocolOutput {
        override def accept(message: ProtocolMessage): Unit = synchronized {
          require(ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), "Activation application output must round-trip")
          messages :+= message
        }
      }
      def snapshot: Vector[ProtocolMessage] = output.synchronized(messages)
      val run = RunId(request.name)
      val application = new TestApplication(run, identity, Vector(() => suite), context, output)
      application.accept(ProtocolMessage.Discover(run, identity.build, identity.target)).flatMap { _ =>
        val catalogue = snapshot.last.asInstanceOf[ProtocolMessage.Discovered].catalogue
        verify(request.name + " discovery suspends activation inputs and resources", configurations.get() == 0 && acquired.get() == 0 && bodies.get() == 0)
        val selection = if (request.unknownId) Selection.Only(Vector.empty, Vector(catalogue.tests.head.id.copy(path = Vector("missing")))) else Selection.All
        val runRequest = RunRequest(identity, selection, request.overrides)
        def command(operation: RequestOperation): Future[Unit] = application.accept(ProtocolMessage.Request(operation, run, runRequest))
        command(RequestOperation.Resolve).flatMap { _ =>
          val resolution = snapshot.last match {
            case ProtocolMessage.Resolved(_, selected) => Right(selected)
            case ProtocolMessage.Rejected(_, failure) => Left(failure)
            case other => throw new IllegalStateException("Unexpected activation resolution: " + other)
          }
          request.expected match {
            case Some(expected) =>
              verify(request.name + " resolves effective activation before filtering", resolution.exists(value => value.tests.size == 2 && value.tests.forall(test => test.settings.axes.contains(AxisChoice(AxisId(Mode.name), AxisValue(expected))) && test.settings.memoization == (request.overrides.memoization != MemoizationOverride.Disabled))))
            case None => verify(request.name + " rejects its explicit selection before provisioning", resolution.left.toOption.exists(failure => failure.phase == FailurePhase.Selection && failure.message.nonEmpty))
          }
          val planning = resolution match {
            case Left(_) => Future.unit
            case Right(resolved) => command(RequestOperation.Plan).flatMap { _ =>
              val description = snapshot.last.asInstanceOf[ProtocolMessage.Planned].plan
              verify(request.name + " plan output retains resolved activation and logical identities", description.selection == resolved && description.selection.tests.map(_.id) == catalogue.tests.map(_.id))
              val allocations = description.inspection.scopes.filter(_.steps.exists(step => description.inspection.keys.exists(key => key.id == step.key && key.displayName == DIKey[Resource].toString) && step.operation == PlanOperation.AllocateResource))
              val sharedScope = if (request.overrides.memoization == MemoizationOverride.Disabled) {
                allocations.size == 2 && allocations.forall(scope => scope.kind == PlanScopeKind.Test && scope.tests.size == 1)
              } else allocations.size == 1 && allocations.head.kind == PlanScopeKind.Memoization && allocations.head.tests.toSet == catalogue.tests.map(_.id).toSet
              verify(request.name + " plan output describes actual resource sharing boundaries", sharedScope)
              verify(request.name + " plan inspection provisions no application resources or bodies", acquired.get() == 0 && released.get() == 0 && bodies.get() == 0 && shared.get() == 0)
              val frame = ProtocolMessage.Planned(run, description)
              verify(request.name + " plan output round-trips through the protocol", ProtocolCodec.decode(ProtocolCodec.encode(frame)) == Right(frame))
              command(RequestOperation.Plan).map { _ => verify(request.name + " repeated application inspection retains the prepared plan", snapshot.last == frame && acquired.get() == 0 && bodies.get() == 0) }
            }
          }
          planning.flatMap(_ => command(RequestOperation.Execute)).map { _ =>
            val expectedBodies = if (request.expected.isDefined) 2 else 0
            verify(request.name + " executes its resolved choice with its intended lifetime", acquired.get() == request.resources && released.get() == request.resources && bodies.get() == expectedBodies && shared.get() == expectedBodies && wrongChoice.get() == 0)
            val successful = snapshot.last match {
              case ProtocolMessage.Completed(outcome) =>
                verify(request.name + " execution agrees with resolution or explains rejection", request.expected.isDefined && outcome.successful && outcome.results.map(_.id) == catalogue.tests.map(_.id))
                val events = snapshot.collect { case event: ProtocolMessage.Event => event }
                verify(request.name + " application event stream finishes after release", events.map(_.sequence) == events.indices.map(_.toLong).toVector && events.last.event == RunEvent.Finished(run, outcome) && released.get() == request.resources)
                outcome.successful
              case ProtocolMessage.Rejected(_, failure) =>
                verify(request.name + " execution agrees with resolution or explains rejection", request.expected.isEmpty && failure.phase == FailurePhase.Selection && resolution.left.toOption.contains(failure))
                verify(request.name + " rejected application emits no execution or successful terminal frames", !snapshot.exists { case _: ProtocolMessage.Event => true; case _: ProtocolMessage.Completed => true; case _ => false })
                false
              case other => throw new IllegalStateException("Unexpected activation execution: " + other)
            }
            verify(request.name + " retains one suite configuration snapshot", configurations.get() == (if (request.unknownId) 0 else 1))
            println("DISTAGE_SPEC_ACTIVATION name=" + request.name + " resources=" + acquired.get() + " bodies=" + bodies.get() + " successful=" + successful)
          }
        }
      }
    } }
  }
}
