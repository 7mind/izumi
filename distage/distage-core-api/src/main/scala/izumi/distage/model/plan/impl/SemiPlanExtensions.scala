package izumi.distage.model.plan.impl

import cats.Applicative
import cats.kernel.Monoid
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, SemiplanOp}
import izumi.distage.model.plan.impl.PlanCatsSyntaxImpl.{CatsMonoid, ResolveImportFSemiPlanPartiallyApplied, resolveImportsImpl1}
import izumi.distage.model.plan.impl.SemiPlanExtensions.SemiPlanExts
import izumi.distage.model.plan.{GCMode, SemiPlan}
import izumi.reflect.Tag

import scala.language.implicitConversions

private[plan] trait SemiPlanExtensions extends Any { this: SemiPlan.type =>
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have cats-effect as a dependency without REQUIRING a cats-effect dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  implicit final def optionalCatsMonoidForSemiplan[K[_]: CatsMonoid]: K[SemiPlan] =
    new Monoid[SemiPlan] {
      override def empty: SemiPlan = SemiPlan(Vector.empty, GCMode.NoGC)

      override def combine(x: SemiPlan, y: SemiPlan): SemiPlan = x ++ y
    }.asInstanceOf[K[SemiPlan]]

  @inline implicit final def toSemiPlanExts(plan: SemiPlan): SemiPlanExts = new SemiPlanExts(plan)
}

private[plan] object SemiPlanExtensions {

  import cats.instances.vector._
  import cats.syntax.functor._
  import cats.syntax.traverse._

  final class SemiPlanExts(private val plan: SemiPlan) extends AnyVal {
    def traverse[F[_]: Applicative](f: SemiplanOp => F[SemiplanOp]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => plan.copy(steps = s))

    def flatMapF[F[_]: Applicative](f: SemiplanOp => F[Seq[SemiplanOp]]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => plan.copy(steps = s.flatten))

    def resolveImportF[T]: ResolveImportFSemiPlanPartiallyApplied[T] = new ResolveImportFSemiPlanPartiallyApplied(plan)

    def resolveImportF[F[_]: Applicative, T: Tag](f: F[T]): F[SemiPlan] = resolveImportF[T](f)

    def resolveImportsF[F[_]: Applicative](f: PartialFunction[ImportDependency, F[Any]]): F[SemiPlan] =
      resolveImportsImpl1(f, plan.steps).map(s => plan.copy(steps = s))
  }
}
