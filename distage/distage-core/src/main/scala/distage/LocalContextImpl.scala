package distage

import distage.LocalContextImpl.LocalInstance
import izumi.distage.LocalContext
import izumi.distage.model.definition.Identifier
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer}

class LocalContextImpl[F[_]: QuasiIO: TagK](parent: Locator, plan: Plan, values: Map[DIKey, LocalInstance[AnyRef]]) extends LocalContext[F] {
  final def add[T <: AnyRef: Tag](value: T)(implicit pos: CodePositionMaterializer): LocalContext[F] = {
    new LocalContextImpl(parent, plan, values ++ Map(DIKey.get[T] -> LocalInstance(value, pos.get)))
  }

  final def add[T <: AnyRef: Tag](id: Identifier, value: T)(implicit pos: CodePositionMaterializer): LocalContext[F] = {
    new LocalContextImpl(parent, plan, values ++ Map(DIKey[T](id) -> LocalInstance(value, pos.get)))
  }

  final def produceRun[A](function: Functoid[F[A]]): F[A] = {
    val lookup = values.get.unlift.compose(PartialFunction.fromFunction((i: ImportDependency) => i.target))
    val imported = plan.resolveImports(lookup)
    Injector.inherit(parent).produce(imported).run(function)
  }
}

object LocalContextImpl {
  private case class LocalInstance[+T](value: T, position: CodePosition)
}
