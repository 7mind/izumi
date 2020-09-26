package izumi.distage.model.plan.impl

import cats.Applicative
import izumi.distage.model.plan.ExecutableOp.WiringOp.UseInstance
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, SemiplanOp}
import izumi.distage.model.plan.Wiring.SingletonWiring.Instance
import izumi.distage.model.plan.{ExecutableOp, OrderedPlan, SemiPlan}
import izumi.distage.model.reflection._
import izumi.reflect.Tag

private[plan] object PlanCatsSyntaxImpl {

  final class ResolveImportFSemiPlanPartiallyApplied[T](private val plan: SemiPlan) extends AnyVal {
    def apply[F[_]: Applicative](f: F[T])(implicit ev: Tag[T]): F[SemiPlan] =
      plan.resolveImportsF[F] {
        case i if i.target == DIKey.get[T] => f.asInstanceOf[F[Any]]
      }
  }

  final class ResolveImportFOrderedPlanPartiallyApplied[T](private val plan: OrderedPlan) extends AnyVal {
    def apply[F[_]: Applicative](f: F[T])(implicit ev: Tag[T]): F[OrderedPlan] =
      plan.resolveImportsF[F] {
        case i if i.target == DIKey.get[T] => f.asInstanceOf[F[Any]]
      }
  }

  import cats.instances.vector._
  import cats.syntax.functor._
  import cats.syntax.traverse._

  @inline
  final def resolveImportsImpl[F[_]: Applicative](f: PartialFunction[ImportDependency, F[Any]], steps: Vector[ExecutableOp]): F[Vector[ExecutableOp]] =
    steps.traverse {
      case i: ImportDependency =>
        f.lift(i).map {
          _.map[ExecutableOp](instance => UseInstance(i.target, Instance(i.target.tpe, instance), i.origin))
        } getOrElse Applicative[F].pure(i)
      case op =>
        Applicative[F].pure(op)
    }

  @inline
  final def resolveImportsImpl1[F[_]: Applicative](f: PartialFunction[ImportDependency, F[Any]], steps: Vector[SemiplanOp]): F[Vector[SemiplanOp]] =
    steps.traverse {
      case i: ImportDependency =>
        f.lift(i).map {
          _.map[SemiplanOp](instance => UseInstance(i.target, Instance(i.target.tpe, instance), i.origin))
        } getOrElse Applicative[F].pure(i)
      case op =>
        Applicative[F].pure(op)
    }
}
