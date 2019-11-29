package izumi.distage.model.plan.impl

import cats.Applicative
import cats.kernel.Monoid
import izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceInstance
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, SemiplanOp}
import izumi.distage.model.plan.{ExecutableOp, OrderedPlan, SemiPlan}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.SingletonWiring.Instance
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._


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

private[plan] object SemiPlanOrderedPlanInstances {

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

  @inline
  final def resolveImportsImpl1[F[_] : Applicative](f: PartialFunction[ImportDependency, F[Any]], steps: Vector[SemiplanOp]): F[Vector[SemiplanOp]] =
    steps.traverse {
      case i: ImportDependency =>
        f.lift(i).map {
          _.map[SemiplanOp](instance => ReferenceInstance(i.target, Instance(i.target.tpe, instance), i.origin))
        } getOrElse Applicative[F].pure(i)
      case op =>
        Applicative[F].pure(op)
    }
}
