package izumi.distage.model.plan.impl

import izumi.distage.model.plan.ExecutableOp.{ImportDependency, SemiplanOp}
import izumi.distage.model.plan.impl.OrderedPlanExtensions.{OrderedPlanCatsOps, OrderedPlanRenderOps}
import izumi.distage.model.plan.impl.PlanCatsSyntaxImpl.{ResolveImportFOrderedPlanPartiallyApplied, resolveImportsImpl}
import izumi.distage.model.plan.repr.{CompactOrderedPlanFormatter, DepTreeRenderer}
import izumi.distage.model.plan.topology.DepTreeNode.DepNode
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.plan.{OrderedPlan, Roots, SemiPlan}
import izumi.functional.Renderable
import izumi.reflect.Tag

import scala.language.implicitConversions

private[plan] trait OrderedPlanExtensions extends Any { this: OrderedPlan.type =>
  final def empty: OrderedPlan = OrderedPlan(Vector.empty, Set.empty, PlanTopology.empty)

  @inline implicit final def defaultFormatter: Renderable[OrderedPlan] = CompactOrderedPlanFormatter

  @inline implicit final def toOrderedPlanRenderOps(plan: OrderedPlan): OrderedPlanRenderOps = new OrderedPlanRenderOps(plan)
  @inline implicit final def toOrderedPlanCatsOps(plan: OrderedPlan): OrderedPlanCatsOps = new OrderedPlanCatsOps(plan)
}

private[plan] object OrderedPlanExtensions {

  final class OrderedPlanRenderOps(private val plan: OrderedPlan) extends AnyVal {
    /**
      * Render with compact type names
      *
      * @see [[OrderedPlanExtensions#defaultFormatter]]
      */
    def render()(implicit ev: Renderable[OrderedPlan]): String = ev.render(plan)
    def renderDeps(node: DepNode): String = new DepTreeRenderer(node, plan).render()

    def renderAllDeps(): String = {
      val effectiveRoots = plan.keys.filter(k => plan.topology.dependees.direct(k).isEmpty)
      effectiveRoots.map(root => plan.topology.dependencies.tree(root)).map(renderDeps).mkString("\n")
    }
  }

  final class OrderedPlanCatsOps(private val plan: OrderedPlan) extends AnyVal {

    import cats.Applicative
    import cats.instances.vector._
    import cats.syntax.functor._
    import cats.syntax.traverse._

    def traverse[F[_]: Applicative](f: SemiplanOp => F[SemiplanOp]): F[SemiPlan] =
      plan.toSemi.steps.traverse(f).map(SemiPlan(_, Roots(plan.declaredRoots)))

    def flatMapF[F[_]: Applicative](f: SemiplanOp => F[Seq[SemiplanOp]]): F[SemiPlan] =
      plan.toSemi.steps.traverse(f).map(s => SemiPlan(s.flatten, Roots(plan.declaredRoots)))

    def resolveImportF[T]: ResolveImportFOrderedPlanPartiallyApplied[T] = new ResolveImportFOrderedPlanPartiallyApplied(plan)
    def resolveImportF[F[_]: Applicative, T: Tag](f: F[T]): F[OrderedPlan] = resolveImportF[T](f)

    def resolveImportsF[F[_]: Applicative](f: PartialFunction[ImportDependency, F[Any]]): F[OrderedPlan] =
      resolveImportsImpl(f, plan.steps).map(s => plan.copy(steps = s))
  }

}
