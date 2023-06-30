package distage

import distage.LocalContextImpl.LocalInstance
import izumi.distage.LocalContext
import izumi.distage.model.definition.Identifier
import izumi.distage.model.exceptions.runtime.UndeclaredKeyException
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.language.{CodePosition, CodePositionMaterializer}

class LocalContextImpl[F[_]: QuasiIO: TagK, R] private (
  externalKeys: Set[DIKey],
  parent: LocatorRef,
  plan: Plan,
  functoid: Functoid[F[R]],
  values: Map[DIKey, LocalInstance[AnyRef]],
) extends LocalContext[F, R] {
  final def provide[T <: Any: Tag](value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R] = {
    val key = DIKey.get[T]
    doAdd(value.asInstanceOf[AnyRef], pos, key)
  }

  private def doAdd(value: AnyRef, pos: CodePositionMaterializer, key: DIKey) = {
    if (!externalKeys.contains(key)) {
      throw new UndeclaredKeyException(s"Key $key is not declared as an external key for this local context. The key is declared at ${pos.get.position.toString}", key)
    }

    new LocalContextImpl(
      externalKeys,
      parent,
      plan,
      functoid,
      values ++ Map(key -> LocalInstance(value, pos.get)),
    )
  }

  final def add[T <: Any: Tag](id: Identifier, value: T)(implicit pos: CodePositionMaterializer): LocalContext[F, R] = {
    val key = DIKey[T](id)
    doAdd(value.asInstanceOf[AnyRef], pos, key)
  }

  override def produceRun(): F[R] = {
    val lookup: PartialFunction[ImportDependency, AnyRef] = {
      case i: ImportDependency if values.contains(i.target) =>
        values(i.target).value
    }

    val imported = plan.resolveImports(lookup)
    Injector.inherit(parent.get).produce(imported).run(functoid)

  }
}

object LocalContextImpl {
  def empty[F[_]: QuasiIO: TagK, R](externalKeys: Set[DIKey], locatorRef: LocatorRef, subplan: Plan, impl: Functoid[F[R]]) =
    new LocalContextImpl[F, R](externalKeys, locatorRef, subplan, impl, Map.empty)

  private case class LocalInstance[+T](value: T, position: CodePosition)
}
