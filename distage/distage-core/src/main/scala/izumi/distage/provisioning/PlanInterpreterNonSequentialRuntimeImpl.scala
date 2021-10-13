//package izumi.distage.provisioning
//
//import distage.Id
//import izumi.distage.LocatorDefaultImpl
//import izumi.distage.model.Locator
//import izumi.distage.model.Locator.LocatorMeta
//import izumi.distage.model.definition.Lifecycle
//import izumi.distage.model.effect.QuasiIO
//import izumi.distage.model.effect.QuasiIO.syntax.*
//import izumi.distage.model.exceptions.{IncompatibleEffectTypesException, ProvisionerIssue}
//import izumi.distage.model.plan.ExecutableOp.{MonadicOp, _}
//import izumi.distage.model.plan.{DIPlan, ExecutableOp}
//import izumi.distage.model.planning.PlanAnalyzer
//import izumi.distage.model.provisioning.*
//import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FailedProvisionMeta, Finalizer, FinalizerFilter}
//import izumi.distage.model.provisioning.strategies.*
//import izumi.distage.model.recursive.LocatorRef
//import izumi.distage.model.reflection.*
//import izumi.fundamentals.graphs.struct.IncidenceMatrix
//import izumi.reflect.TagK
//
//import java.util.concurrent.atomic.AtomicReference
//import scala.annotation.nowarn
//import scala.collection.mutable
//import scala.collection.mutable.ArrayBuffer
//import scala.concurrent.duration.Duration
//import scala.util.{Failure, Success, Try}
//
//object PlanInterpreterNonSequentialRuntimeImpl {
////  import izumi.distage.model.plan.repr._
////  import izumi.distage.model.plan.topology.PlanTopology
////  import izumi.distage.model.reflection._
////  import izumi.fundamentals.platform.strings.IzString.toRichString
////
////  /**
////    * Linearized graph which is ready to be consumed by linear executors
////    *
////    * May contain cyclic dependencies resolved with proxies
////    */
////  @deprecated("should be removed once we finish transition to parallel interpreter", "13/04/2021")
////  private final case class OrderedPlan(
////    steps: Vector[ExecutableOp],
////    topology: PlanTopology,
////  ) {
////    private def keys: Set[DIKey] = {
////      steps.map(_.target).toSet
////    }
////
////    /** Print while omitting package names for unambiguous types */
////    override def toString: String = {
////      val minimizer = KeyMinimizer(this.keys, DIRendering.colorsEnabled)
////      val tf = TypeFormatter.minimized(minimizer)
////      val kf = KeyFormatter.minimized(minimizer)
////      val opFormatter = OpFormatter(kf, tf, DIRendering.colorsEnabled)
////
////      this.steps.map(opFormatter.format).mkString("\n").listing()
////    }
////  }
//
//}
//
//class PlanInterpreterNonSequentialRuntimeImpl(
//  setStrategy: SetStrategy,
//  proxyStrategy: ProxyStrategy,
//  providerStrategy: ProviderStrategy,
//  importStrategy: ImportStrategy,
//  instanceStrategy: InstanceStrategy,
//  effectStrategy: EffectStrategy,
//  resourceStrategy: ResourceStrategy,
//  failureHandler: ProvisioningFailureInterceptor,
//  verifier: ProvisionOperationVerifier,
//  fullStackTraces: Boolean @Id("izumi.distage.interpreter.full-stacktraces"),
//  analyzer: PlanAnalyzer,
//) extends PlanInterpreter
//  with OperationExecutor {
//
//  type OperationMetadata = Long
//
//  override def run[F[_]: TagK: QuasiIO](
//    plan: DIPlan,
//    parentLocator: Locator,
//    filterFinalizers: FinalizerFilter[F],
//  ): Lifecycle[F, Either[FailedProvision[F], Locator]] = {
//    val F: QuasiIO[F] = implicitly[QuasiIO[F]]
//    Lifecycle.make(
//      acquire = instantiateImpl(plan, parentLocator)
//    )(release = {
//      resource =>
//        val finalizers = resource match {
//          case Left(failedProvision) => failedProvision.failed.finalizers
//          case Right(locator) => locator.finalizers
//        }
//        filterFinalizers.filter(finalizers).foldLeft(F.unit) {
//          case (acc, f) => acc.guarantee(F.suspendF(f.effect()))
//        }
//    })
//  }
//
//  private def process(context: ProvisioningKeyProvider, op: ExecutableOp) = {
//    op match {
//      case op: ImportDependency =>
//        importStrategy.importDependency(context, this, op)
//      case op: NonImportOp =>
//        execute(context, op)
//    }
//  }
//
//  private[this] def instantiateImpl[F[_]: TagK](
////    plan: OrderedPlan,
//    diplan: DIPlan,
//    parentContext: Locator,
//  )(implicit F: QuasiIO[F]
//  ): F[Either[FailedProvision[F], LocatorDefaultImpl[F]]] = {
//    val mutProvisioningContext = Prov.make(diplan, parentContext)
//
//    def run(state: TraversalState) = {
//      state.next() match {
//        case TraversalState.Step(next, steps) =>
//          val ops = steps.toSeq.map(k => diplan.plan.meta.nodes(k))
//          ops.map {}
//        case TraversalState.Done() =>
//        case TraversalState.Problem(left) =>
//      }
//    }
//    run(TraversalState(diplan.plan.predecessors.links))
//
//  }
//
//  override def execute[F[_]: TagK](context: ProvisioningKeyProvider, step: NonImportOp)(implicit F: QuasiIO[F]): F[Seq[NewObjectOp]] = {
//    step match {
//      case op: CreateSet =>
//        F pure setStrategy.makeSet(context, this, op)
//
//      case op: WiringOp =>
//        F pure execute(context, op)
//
//      case op: ProxyOp.MakeProxy =>
//        F pure proxyStrategy.makeProxy(context, this, op)
//
//      case op: ProxyOp.InitProxy =>
//        proxyStrategy.initProxy(context, this, op)
//
//      case op: MonadicOp.ExecuteEffect =>
//        F widen effectStrategy.executeEffect[F](context, this, op)
//
//      case op: MonadicOp.AllocateResource =>
//        F widen resourceStrategy.allocateResource[F](context, this, op)
//    }
//  }
//
//  override def execute(context: ProvisioningKeyProvider, step: WiringOp): Seq[NewObjectOp] = {
//    step match {
//      case op: WiringOp.UseInstance =>
//        instanceStrategy.getInstance(context, this, op)
//
//      case op: WiringOp.ReferenceKey =>
//        instanceStrategy.getInstance(context, this, op)
//
//      case op: WiringOp.CallProvider =>
//        providerStrategy.callProvider(context, this, op)
//    }
//  }
//
//  private[this] def interpretResult[F[_]: TagK](active: ProvisionMutable[F], result: NewObjectOp): Unit = {
//    result match {
//      case NewObjectOp.NewImport(target, instance) =>
//        verifier.verify(target, active.imports.keySet, instance, s"import")
//        active.imports += (target -> instance)
//
//      case NewObjectOp.NewInstance(target, instance) =>
//        verifier.verify(target, active.instances.keySet, instance, "instance")
//        active.instances += (target -> instance)
//
//      case r @ NewObjectOp.NewResource(target, instance, _) =>
//        verifier.verify(target, active.instances.keySet, instance, "resource")
//        active.instances += (target -> instance)
//        val finalizer = r.asInstanceOf[NewObjectOp.NewResource[F]].finalizer
//        active.finalizers prepend Finalizer[F](target, finalizer)
//
//      case r @ NewObjectOp.NewFinalizer(target, _) =>
//        val finalizer = r.asInstanceOf[NewObjectOp.NewFinalizer[F]].finalizer
//        active.finalizers prepend Finalizer[F](target, finalizer)
//
//      case NewObjectOp.UpdatedSet(target, instance) =>
//        verifier.verify(target, active.instances.keySet, instance, "set")
//        active.instances += (target -> instance)
//    }
//  }
//
//  private[this] def verifyEffectType[F[_]: TagK](
//    ops: Seq[NonImportOp]
//  )(implicit F: QuasiIO[F]
//  ): F[Either[Seq[IncompatibleEffectTypesException], Unit]] = {
//    val monadicOps = ops.collect { case m: MonadicOp => m }
//    val badOps = monadicOps
//      .filter(_.isIncompatibleEffectType[F])
//      .map {
//        op =>
//          IncompatibleEffectTypesException(op, op.provisionerEffectType[F], op.actionEffectType)
//      }
//
//    F.ifThenElse(badOps.isEmpty)(F.pure(Right(())), F.pure(Left(badOps)))
//  }
//
//}
