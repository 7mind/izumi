package distage.interop

import _root_.cats.Applicative
import _root_.cats.instances.vector._
import _root_.cats.syntax.functor._
import _root_.cats.syntax.traverse._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceInstance
import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, OrderedPlan, SemiPlan}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring.Instance
import distage._

package object cats
  extends DistageInteropCats {

  implicit final class SemiPlanExts(private val plan: SemiPlan) extends AnyVal {
    def traverse[F[_]: Applicative](f: ExecutableOp => F[ExecutableOp]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => plan.copy(steps = s))

    def flatMapF[F[_]: Applicative](f: ExecutableOp => F[Seq[ExecutableOp]]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => plan.copy(steps = s.flatten))

    def resolveImportF[T]: ResolveImportFSemiPlanPartiallyApplied[T] = new ResolveImportFSemiPlanPartiallyApplied(plan)

    def resolveImportF[F[_]: Applicative, T: Tag](f: F[T]): F[SemiPlan] = resolveImportF[T](f)

    def resolveImportsF[F[_]: Applicative](f: PartialFunction[ImportDependency, F[Any]]): F[SemiPlan] =
      resolveImportsImpl(f, plan.steps).map(s => plan.copy(steps = s))
  }

  implicit final class OrderedPlanExts(private val plan: OrderedPlan) extends AnyVal {
    def traverse[F[_]: Applicative](f: ExecutableOp => F[ExecutableOp]): F[SemiPlan] =
      plan.steps.traverse(f).map(SemiPlan(plan.definition, _, plan.roots))

    def flatMapF[F[_]: Applicative](f: ExecutableOp => F[Seq[ExecutableOp]]): F[SemiPlan] =
      plan.steps.traverse(f).map(s => SemiPlan(plan.definition, s.flatten, plan.roots))

    def resolveImportF[T]: ResolveImportFOrderedPlanPartiallyApplied[T] = new ResolveImportFOrderedPlanPartiallyApplied(plan)

    def resolveImportF[F[_]: Applicative, T: Tag](f: F[T]): F[OrderedPlan] = resolveImportF[T](f)

    def resolveImportsF[F[_]: Applicative](f: PartialFunction[ImportDependency, F[Any]]): F[OrderedPlan] =
      resolveImportsImpl(f, plan.steps).map(s => plan.copy(steps = s))
  }

  @inline
  private[cats] def resolveImportsImpl[F[_]: Applicative](f: PartialFunction[ImportDependency, F[Any]], steps: Vector[ExecutableOp]): F[Vector[ExecutableOp]] =
    steps.traverse {
      case i: ImportDependency =>
        f.lift(i).map {
          _.map[ExecutableOp](instance => ReferenceInstance(i.target, Instance(i.target.tpe, instance), i.origin))
        } getOrElse Applicative[F].pure(i)
      case op =>
        Applicative[F].pure(op)
    }

}
