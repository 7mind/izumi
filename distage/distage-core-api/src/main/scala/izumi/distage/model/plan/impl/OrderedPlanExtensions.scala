package izumi.distage.model.plan.impl

import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.plan.impl.OrderedPlanExtensions.{OrderedPlanCatsOps, OrderedPlanRenderOps}
import izumi.distage.model.plan.impl.PlanCatsSyntaxImpl.{ResolveImportFOrderedPlanPartiallyApplied, resolveImportsImpl}
import izumi.distage.model.plan.repr.CompactOrderedPlanFormatter
import izumi.distage.model.plan.topology.PlanTopology
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
//    def renderDeps(node: DepNode): String = new DepTreeRenderer(node, plan.index).render()

//    def renderAllDeps(): String = {
//      val effectiveRoots = plan.keys.filter(k => plan.topology.dependees.direct(k).isEmpty)
//      effectiveRoots.map(root => plan.topology.dependencies.tree(root)).map(renderDeps).mkString("\n")
//    }
  }

  final class OrderedPlanCatsOps(private val plan: OrderedPlan) extends AnyVal {

    import cats.Applicative
    import cats.syntax.functor._

    def resolveImportF[T]: ResolveImportFOrderedPlanPartiallyApplied[T] = new ResolveImportFOrderedPlanPartiallyApplied(plan)
    def resolveImportF[F[_]: Applicative, T: Tag](f: F[T]): F[OrderedPlan] = resolveImportF[T](f)

    def resolveImportsF[F[_]: Applicative](f: PartialFunction[ImportDependency, F[Any]]): F[OrderedPlan] =
      resolveImportsImpl(f, plan.steps).map(s => plan.copy(steps = s))
  }

}
