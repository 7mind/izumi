package izumi.distage

import distage.Injector
import izumi.distage.model.definition.Identifier
import izumi.distage.model.exceptions.runtime.UndeclaredKeyException
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.plan.Plan
import izumi.distage.model.providers.Functoid
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection.DIKey
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.{Tag, TagK}

final class SubcontextImpl[A] private (
  externalKeys: Set[DIKey],
  parent: LocatorRef,
  val plan: Plan,
  functoid: Functoid[A],
  providedExternals: Map[DIKey, AnyRef],
  selfKey: DIKey,
) extends Subcontext[A] {

  def provide[T: Tag](value: T)(implicit pos: CodePositionMaterializer): Subcontext[A] = {
    val key = DIKey.get[T]
    doAdd(value.asInstanceOf[AnyRef], pos, key)
  }

  def provide[T: Tag](name: Identifier)(value: T)(implicit pos: CodePositionMaterializer): Subcontext[A] = {
    val key = DIKey[T](name)
    doAdd(value.asInstanceOf[AnyRef], pos, key)
  }

  override def produce[F[_]: QuasiIO: TagK](): Lifecycle[F, A] = {
    val lookup: PartialFunction[ImportDependency, AnyRef] = {
      case i: ImportDependency if providedExternals.contains(i.target) =>
        providedExternals(i.target)
      case i: ImportDependency if i.target == selfKey =>
        this
    }
    val imported = plan.resolveImports(lookup)
    Injector
      .inherit(parent.get)
      .produce(imported)
      .map(_.run(functoid))
  }

  override def produceRun[F[_]: QuasiIO: TagK](): F[A] = {
    produce().use(QuasiIO[F].pure(_))
  }

  override def map[B: Tag](f: A => B): Subcontext[B] = {
    new SubcontextImpl(externalKeys, parent, plan, functoid.map[B](f), providedExternals, selfKey)
  }

  private def doAdd(value: AnyRef, pos: CodePositionMaterializer, key: DIKey): SubcontextImpl[A] = {
    if (!externalKeys.contains(key)) {
      throw new UndeclaredKeyException(s"Key $key is not declared as an external key for this local context. The value is provided at ${pos.get.position.toString}", key)
    }

    new SubcontextImpl(
      externalKeys = externalKeys,
      parent = parent,
      plan = plan,
      functoid = functoid,
      providedExternals = providedExternals ++ Map(key -> value),
      selfKey = selfKey,
    )
  }

}

object SubcontextImpl {
  def empty[A](externalKeys: Set[DIKey], locatorRef: LocatorRef, subplan: Plan, impl: Functoid[A], selfKey: DIKey) =
    new SubcontextImpl[A](externalKeys, locatorRef, subplan, impl, Map.empty, selfKey)
}
