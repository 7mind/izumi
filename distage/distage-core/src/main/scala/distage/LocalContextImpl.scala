package distage

import distage.LocalContextImpl.LocalInstance
import izumi.distage.LocalContext
import izumi.distage.model.definition.Identifier
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer}

class LocalContextImpl[F[_]: QuasiIO: TagK, R](parent: Locator, plan: Plan, functoid: Functoid[F[R]], values: Map[DIKey, LocalInstance[AnyRef]])
  extends LocalContext[F, R] {
  final def add[T <: AnyRef: Tag](value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R] = {
    new LocalContextImpl(parent, plan, functoid, values ++ Map(DIKey.get[T] -> LocalInstance(value, pos.get)))
  }

  final def add[T <: AnyRef: Tag](id: Identifier, value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R] = {
    new LocalContextImpl(parent, plan, functoid, values ++ Map(DIKey[T](id) -> LocalInstance(value, pos.get)))
  }

  override def produceRun(): F[R] = {
    val lookup = values.get.unlift.compose(PartialFunction.fromFunction((i: ImportDependency) => i.target))
    val imported = plan.resolveImports(lookup)
    Injector.inherit(parent).produce(imported).run(functoid)

  }
}

object LocalContextImpl {
  private case class LocalInstance[+T](value: T, position: CodePosition)
}
