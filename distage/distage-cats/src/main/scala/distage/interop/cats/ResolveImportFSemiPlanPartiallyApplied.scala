package distage.interop.cats

import _root_.cats.Applicative
import distage._

private[cats] final class ResolveImportFSemiPlanPartiallyApplied[T](private val plan: SemiPlan) extends AnyVal {
  def apply[F[_]: Applicative](f: F[T])(implicit ev: Tag[T]): F[SemiPlan] =
    plan.resolveImportsF[F] {
      case i if i.target == DIKey.get[T] => f.asInstanceOf[F[Any]]
    }
}

private[cats] final class ResolveImportFOrderedPlanPartiallyApplied[T](private val plan: OrderedPlan) extends AnyVal {
  def apply[F[_]: Applicative](f: F[T])(implicit ev: Tag[T]): F[OrderedPlan] =
    plan.resolveImportsF[F] {
      case i if i.target == DIKey.get[T] => f.asInstanceOf[F[Any]]
    }
}
