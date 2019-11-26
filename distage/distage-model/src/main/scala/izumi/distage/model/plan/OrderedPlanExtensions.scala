package izumi.distage.model.plan

import cats.Applicative
import izumi.distage.model.GCMode
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.plan.OrderedPlanExtensions.{OrderedPlanExts, OrderedPlanSyntax}
import izumi.distage.model.plan.SemiPlanOrderedPlanInstances.resolveImportsImpl
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.functional.Renderable

import scala.language.implicitConversions

trait OrderedPlanExtensions {
  implicit val defaultFormatter: Renderable[OrderedPlan] = CompactPlanFormatter.OrderedPlanFormatter

  def empty: OrderedPlan = OrderedPlan(Vector.empty, GCMode.NoGC, PlanTopologyImmutable(DependencyGraph(Map.empty, DependencyKind.Depends), DependencyGraph(Map.empty, DependencyKind.Required)))

  @inline implicit final def toPlanSyntax(plan: OrderedPlan): OrderedPlanSyntax = new OrderedPlanSyntax(plan)
  @inline implicit final def toOrderedPlanExts(plan: OrderedPlan): OrderedPlanExts = new OrderedPlanExts(plan)
}

private[plan] object OrderedPlanExtensions {
  final class OrderedPlanSyntax(private val plan: OrderedPlan) extends AnyVal {
    def render()(implicit ev: Renderable[OrderedPlan]): String = ev.render(plan)

    def renderDeps(node: DepNode): String = new DepTreeRenderer(node, plan).render()

    def renderAllDeps(): String = {
      val effectiveRoots = plan.keys.filter(k => plan.topology.dependees.direct(k).isEmpty)
      effectiveRoots.map(root => plan.topology.dependencies.tree(root)).map(renderDeps).mkString("\n")
    }
  }

  final class OrderedPlanExts(private val plan: OrderedPlan) extends AnyVal {

    import cats.instances.vector._
    import cats.syntax.functor._
    import cats.syntax.traverse._

    def traverse[F[_] : Applicative](f: ExecutableOp => F[ExecutableOp]): F[SemiPlan] =
      plan.steps.traverse(f).map(SemiPlan(_, plan.gcMode))

    def flatMapF[F[_] : Applicative](f: ExecutableOp => F[Seq[ExecutableOp]]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => SemiPlan(s.flatten, plan.gcMode))

    def resolveImportF[T]: ResolveImportFOrderedPlanPartiallyApplied[T] = new ResolveImportFOrderedPlanPartiallyApplied(plan)

    def resolveImportF[F[_] : Applicative, T: Tag](f: F[T]): F[OrderedPlan] = resolveImportF[T](f)

    def resolveImportsF[F[_] : Applicative](f: PartialFunction[ImportDependency, F[Any]]): F[OrderedPlan] =
      resolveImportsImpl(f, plan.steps).map(s => plan.copy(steps = s))
  }

}