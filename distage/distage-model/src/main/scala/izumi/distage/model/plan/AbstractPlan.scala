package izumi.distage.model.plan

import cats.Applicative
import cats.kernel.Monoid
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceInstance
import izumi.distage.model.plan.SemiPlanOrderedPlanInstances.{CatsMonoid, resolveImportsImpl}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring.Instance
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.model.{GCMode, Locator}
import izumi.functional.Renderable

// TODO: we need to parameterize plans with op types to avoid possibility of having proxy ops in semiplan


sealed trait AbstractPlan extends ExtendedPlanAPI {
  def definition: ModuleBase

  def gcMode: GCMode

  def steps: Seq[ExecutableOp]

  def index: Map[RuntimeDIUniverse.DIKey, ExecutableOp]


  def resolveImports(f: PartialFunction[ImportDependency, Any]): AbstractPlan

  def resolveImport[T: Tag](instance: T): AbstractPlan

  def resolveImport[T: Tag](id: String)(instance: T): AbstractPlan

  def locateImports(locator: Locator): AbstractPlan


  override def toString: String = {
    steps.map(_.toString).mkString("\n")
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

sealed trait ExtendedPlan extends AbstractPlan with WithLazyIndex

/**
  * An unordered plan.
  *
  * You can turn into an [[OrderedPlan]] via [[izumi.distage.model.Planner.finish]]
  */
final case class SemiPlan(/*protected val definition: ModuleBase,*/ steps: Vector[ExecutableOp], gcMode: GCMode) extends ExtendedPlan {

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
    resolveImports(Function.unlift(i => locator.lookupLocal[Any](i.target)))
  }

}

object SemiPlan {

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit def optionalCatsMonoidForSemiplan[K[_] : CatsMonoid]: K[SemiPlan] =
    new Monoid[SemiPlan] {
      override def empty: SemiPlan = SemiPlan(/*ModuleBase.empty, */Vector.empty, GCMode.NoGC)

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

final case class OrderedPlan(/*protected val definition: ModuleBase, */steps: Vector[ExecutableOp], gcMode: GCMode, topology: PlanTopology) extends ExtendedPlan {
  /**
    * Be careful, don't use this method blindly, it can disrupt graph connectivity when used improperly.
    *
    * Proper usage assume that `keys` contains complete subgraph reachable from graph roots.
    */
  def replaceWithImports(keys: Set[DIKey]): OrderedPlan = {
    val roots = gcMode.toSet
    val newSteps = steps.flatMap {
      case s if keys.contains(s.target) =>
        val dependees = topology.dependees.direct(s.target)
        if (dependees.diff(keys).nonEmpty || roots.contains(s.target)) {
          val dependees = topology.dependees.transitive(s.target).diff(keys)
          Seq(ImportDependency(s.target, dependees, s.origin.toSynthetic))
        } else {
          Seq.empty
        }
      case s =>
        Seq(s)
    }

    OrderedPlan(
      //definition.drop(keys),
      newSteps,
      gcMode,
      topology.removeKeys(keys),
    )
  }

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
    resolveImports(Function.unlift(i => locator.lookupLocal[Any](i.target)))
  }
}

object OrderedPlan {
  implicit val defaultFormatter: Renderable[OrderedPlan] = CompactPlanFormatter.OrderedPlanFormatter

  def empty: OrderedPlan = OrderedPlan(/*ModuleBase.empty, */Vector.empty, GCMode.NoGC, PlanTopologyImmutable(DependencyGraph(Map.empty, DependencyKind.Depends), DependencyGraph(Map.empty, DependencyKind.Required)))

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
      plan.steps.traverse(f).map(SemiPlan(/*plan.definition,*/ _, plan.gcMode))

    def flatMapF[F[_] : Applicative](f: ExecutableOp => F[Seq[ExecutableOp]]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => SemiPlan(/*plan.definition,*/ s.flatten, plan.gcMode))

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

  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  sealed abstract class CatsMonoid[K[_]]

  object CatsMonoid {
    @inline implicit final def get: CatsMonoid[Monoid] = null
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
