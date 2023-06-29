package distage

import distage.LocalContextImpl.LocalInstance
import izumi.distage.LocalContext
import izumi.distage.model.definition.Identifier
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer}

class LocalContextImpl[F[_]: QuasiIO: TagK, R](parent: LocatorRef, plan: Plan, functoid: Functoid[F[R]], values: Map[DIKey, LocalInstance[AnyRef]])
  extends LocalContext[F, R] {
  final def add[T <: Any: Tag](value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R] = {
    new LocalContextImpl(parent, plan, functoid, values ++ Map(DIKey.get[T] -> LocalInstance(value.asInstanceOf[AnyRef], pos.get)))
  }

  final def add[T <: Any: Tag](id: Identifier, value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R] = {
    new LocalContextImpl(parent, plan, functoid, values ++ Map(DIKey[T](id) -> LocalInstance(value.asInstanceOf[AnyRef], pos.get)))
  }

  override def produceRun(): F[R] = {
    val lookup = (values.get _).unlift.compose(PartialFunction.fromFunction((i: ImportDependency) => i.target)).andThen(_.value)
    val imported = plan.resolveImports(lookup)
    Injector.inherit(parent.get).produce(imported).run(functoid)

  }
}

object LocalContextImpl {
  private case class LocalInstance[+T](value: T, position: CodePosition)
}
