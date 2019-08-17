package izumi.distage.model.plan

import cats.Applicative
import cats.kernel.Monoid
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import izumi.distage.model.plan.ExecutableOp.WiringOp.{CallProvider, ReferenceInstance}
import izumi.distage.model.plan.SemiPlanOrderedPlanInstances.{CatsMonoid, resolveImportsImpl}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring.Instance
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.model.{GCMode, Locator}
import izumi.functional.Renderable

// TODO: we need to parameterize plans with op types to avoid possibility of having proxy ops in semiplan
sealed trait AbstractPlan {
  def definition: ModuleBase

  def steps: Seq[ExecutableOp]

  def gcMode: GCMode

  def resolveImports(f: PartialFunction[ImportDependency, Any]): AbstractPlan

  def resolveImport[T: Tag](instance: T): AbstractPlan

  def resolveImport[T: Tag](id: String)(instance: T): AbstractPlan

  def locateImports(locator: Locator): AbstractPlan

  final lazy val index: Map[DIKey, ExecutableOp] = {
    steps.map(s => s.target -> s).toMap
  }

  /** Get all imports (unresolved dependencies).
   *
   * Note, presence of imports does not automatically mean that a plan is invalid,
   * Imports may be fulfilled by a `Locator`, by BootstrapContext, or they may be materialized by a custom
   * [[izumi.distage.model.provisioning.strategies.ImportStrategy]]
   * */
  final def getImports: Seq[ImportDependency] =
    steps.collect { case i: ImportDependency => i }

  final def resolveImportsOp(f: PartialFunction[ImportDependency, Seq[ExecutableOp]]): SemiPlan = {
    SemiPlan(definition, steps = AbstractPlan.resolveImports(f, steps.toVector), gcMode)
  }

  final def providerImport[T](function: ProviderMagnet[T]): SemiPlan = {
    resolveImportsOp {
      case i if i.target.tpe == function.get.ret =>
        Seq(CallProvider(i.target, SingletonWiring.Function(function.get, function.get.associations), i.origin))
    }
  }

  final def providerImport[T](id: String)(function: ProviderMagnet[T]): SemiPlan = {
    resolveImportsOp {
      case i if i.target == DIKey.IdKey(function.get.ret, id) =>
        Seq(CallProvider(i.target, SingletonWiring.Function(function.get, function.get.associations), i.origin))
    }
  }

  final def keys: Set[DIKey] = {
    steps.map(_.target).toSet
  }

  final def filter[T: Tag]: Seq[ExecutableOp] = {
    steps.filter(_.target == DIKey.get[T])
  }

  final def map(f: ExecutableOp => ExecutableOp): SemiPlan = {
    val SemiPlan(definition, steps, gcMode) = toSemi
    SemiPlan(definition, steps.map(f), gcMode)
  }

  final def flatMap(f: ExecutableOp => Seq[ExecutableOp]): SemiPlan = {
    val SemiPlan(definition, steps, gcMode) = toSemi
    SemiPlan(definition, steps.flatMap(f), gcMode)
  }

  final def collect(f: PartialFunction[ExecutableOp, ExecutableOp]): SemiPlan = {
    val SemiPlan(definition, steps, gcMode) = toSemi
    SemiPlan(definition, steps.collect(f), gcMode)
  }

  final def ++(that: AbstractPlan): SemiPlan = {
    val SemiPlan(definition, steps, gcMode) = toSemi
    val that0 = that.toSemi
    SemiPlan(definition ++ that0.definition, steps ++ that0.steps, gcMode)
  }

  final def collectChildren[T: Tag]: Seq[ExecutableOp] = {
    val parent = SafeType.get[T]
    steps.filter {
      op =>
        val maybeChild = ExecutableOp.instanceType(op)
        maybeChild <:< parent
    }
  }

  final def foldLeft[T](z: T, f: (T, ExecutableOp) => T): T = {
    steps.foldLeft(z)(f)
  }

  override def toString: String = {
    steps.map(_.toString).mkString("\n")
  }

  def toSemi: SemiPlan = {
    val safeSteps = steps.flatMap {
      case _: InitProxy =>
        Seq.empty
      case i: MakeProxy =>
        Seq(i.op)
      case o => Seq(o)
    }
    SemiPlan(definition, safeSteps.toVector, gcMode)
  }

}

object AbstractPlan {
  private[plan] def resolveImports(f: PartialFunction[ImportDependency, Seq[ExecutableOp]], steps: Vector[ExecutableOp]): Vector[ExecutableOp] =
    steps.flatMap {
      case i: ImportDependency =>
        f.lift(i) getOrElse Seq(i)
      case op =>
        Seq(op)
    }

  private[plan] def importToInstances(f: PartialFunction[ImportDependency, Any]): PartialFunction[ImportDependency, Seq[ExecutableOp]] =
    Function.unlift(i => f.lift(i).map(instance => Seq(ReferenceInstance(i.target, Instance(i.target.tpe, instance), i.origin))))
}

/**
 * An unordered plan.
 *
 * You can turn into an [[OrderedPlan]] via [[izumi.distage.model.Planner.finish]]
 */
final case class SemiPlan(definition: ModuleBase, steps: Vector[ExecutableOp], gcMode: GCMode) extends AbstractPlan {

  override def toSemi: SemiPlan = this

  override def resolveImport[T: Tag](instance: T): SemiPlan =
    resolveImports {
      case i if i.target == DIKey.get[T] =>
        instance
    }

  override def resolveImport[T: Tag](id: String)(instance: T): SemiPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  override def resolveImports(f: PartialFunction[ImportDependency, Any]): SemiPlan = {
    copy(steps = AbstractPlan.resolveImports(AbstractPlan.importToInstances(f), steps))
  }

  override def locateImports(locator: Locator): SemiPlan = {
    resolveImports(Function.unlift(i => locator.lookup[Any](i.target)))
  }

}

object SemiPlan {

  //  implicit def catsKernelStdHashForSemiPlan: Hash[SemiPlan] =
  //    new Hash[SemiPlan] {
  //      override def hash(x: SemiPlan): Int = x.hashCode()
  //
  //      override def eqv(x: SemiPlan, y: SemiPlan): Boolean = x == y
  //    }

  /** Optional instance via https://blog.7mind.io/no-more-orphans.html */
  implicit def optionalCatsMonoidForSemiplan[K[_] : CatsMonoid]: K[SemiPlan] =
    new Monoid[SemiPlan] {
      override def empty: SemiPlan = SemiPlan(ModuleBase.empty, Vector.empty, GCMode.NoGC)

      override def combine(x: SemiPlan, y: SemiPlan): SemiPlan = x ++ y
    }.asInstanceOf[K[SemiPlan]]

  import cats.instances.vector._
  import cats.syntax.functor._
  import cats.syntax.traverse._

  implicit final class SemiPlanExts(private val plan: SemiPlan) extends AnyVal {
    def traverse[F[_] : Applicative](f: ExecutableOp => F[ExecutableOp]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => plan.copy(steps = s))

    def flatMapF[F[_] : Applicative](f: ExecutableOp => F[Seq[ExecutableOp]]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => plan.copy(steps = s.flatten))

    def resolveImportF[T]: ResolveImportFSemiPlanPartiallyApplied[T] = new ResolveImportFSemiPlanPartiallyApplied(plan)

    def resolveImportF[F[_] : Applicative, T: Tag](f: F[T]): F[SemiPlan] = resolveImportF[T](f)

    def resolveImportsF[F[_] : Applicative](f: PartialFunction[ImportDependency, F[Any]]): F[SemiPlan] =
      resolveImportsImpl(f, plan.steps).map(s => plan.copy(steps = s))
  }

}

final case class OrderedPlan(definition: ModuleBase, steps: Vector[ExecutableOp], gcMode: GCMode, topology: PlanTopology) extends AbstractPlan {
  override def resolveImports(f: PartialFunction[ImportDependency, Any]): OrderedPlan = {
    copy(steps = AbstractPlan.resolveImports(AbstractPlan.importToInstances(f), steps))
  }

  override def resolveImport[T: Tag](instance: T): OrderedPlan =
    resolveImports {
      case i if i.target == DIKey.get[T] =>
        instance
    }

  override def resolveImport[T: Tag](id: String)(instance: T): OrderedPlan = {
    resolveImports {
      case i if i.target == DIKey.get[T].named(id) =>
        instance
    }
  }

  override def locateImports(locator: Locator): OrderedPlan = {
    resolveImports(Function.unlift(i => locator.lookup[Any](i.target)))
  }
}

object OrderedPlan {
  implicit val defaultFormatter: Renderable[OrderedPlan] = CompactPlanFormatter.OrderedPlanFormatter

  def empty: OrderedPlan = OrderedPlan(ModuleBase.empty, Vector.empty, GCMode.NoGC, PlanTopologyImmutable(DependencyGraph(Map.empty, DependencyKind.Depends), DependencyGraph(Map.empty, DependencyKind.Required)))

  implicit final class PlanSyntax(private val plan: OrderedPlan) extends AnyVal {
    def render()(implicit ev: Renderable[OrderedPlan]): String = ev.render(plan)

    def renderDeps(node: DepNode): String = new DepTreeRenderer(node, plan).render()

    def renderAllDeps(): String = {
      val effectiveRoots = plan.keys.filter(k => plan.topology.dependees.direct(k).isEmpty)
      effectiveRoots.map(root => plan.topology.dependencies.tree(root)).map(renderDeps).mkString("\n")
    }
  }

  implicit final class OrderedPlanExts(private val plan: OrderedPlan) extends AnyVal {

    import cats.instances.vector._
    import cats.syntax.functor._
    import cats.syntax.traverse._

    def traverse[F[_] : Applicative](f: ExecutableOp => F[ExecutableOp]): F[SemiPlan] =
      plan.steps.traverse(f).map(SemiPlan(plan.definition, _, plan.gcMode))

    def flatMapF[F[_] : Applicative](f: ExecutableOp => F[Seq[ExecutableOp]]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => SemiPlan(plan.definition, s.flatten, plan.gcMode))

    def resolveImportF[T]: ResolveImportFOrderedPlanPartiallyApplied[T] = new ResolveImportFOrderedPlanPartiallyApplied(plan)

    def resolveImportF[F[_] : Applicative, T: Tag](f: F[T]): F[OrderedPlan] = resolveImportF[T](f)

    def resolveImportsF[F[_] : Applicative](f: PartialFunction[ImportDependency, F[Any]]): F[OrderedPlan] =
      resolveImportsImpl(f, plan.steps).map(s => plan.copy(steps = s))
  }

}

private[plan] final class ResolveImportFSemiPlanPartiallyApplied[T](private val plan: SemiPlan) extends AnyVal {
  def apply[F[_] : Applicative](f: F[T])(implicit ev: Tag[T]): F[SemiPlan] =
    plan.resolveImportsF[F] {
      case i if i.target == DIKey.get[T] => f.asInstanceOf[F[Any]]
    }
}

private[plan] final class ResolveImportFOrderedPlanPartiallyApplied[T](private val plan: OrderedPlan) extends AnyVal {
  def apply[F[_] : Applicative](f: F[T])(implicit ev: Tag[T]): F[OrderedPlan] =
    plan.resolveImportsF[F] {
      case i if i.target == DIKey.get[T] => f.asInstanceOf[F[Any]]
    }
}

private object SemiPlanOrderedPlanInstances {

  sealed abstract class CatsMonoid[K[_]]

  object CatsMonoid {
    implicit val get: CatsMonoid[Monoid] = null
  }

  import cats.instances.vector._
  import cats.syntax.functor._
  import cats.syntax.traverse._

  @inline
  final def resolveImportsImpl[F[_] : Applicative](f: PartialFunction[ImportDependency, F[Any]], steps: Vector[ExecutableOp]): F[Vector[ExecutableOp]] =
    steps.traverse {
      case i: ImportDependency =>
        f.lift(i).map {
          _.map[ExecutableOp](instance => ReferenceInstance(i.target, Instance(i.target.tpe, instance), i.origin))
        } getOrElse Applicative[F].pure(i)
      case op =>
        Applicative[F].pure(op)
    }
}
