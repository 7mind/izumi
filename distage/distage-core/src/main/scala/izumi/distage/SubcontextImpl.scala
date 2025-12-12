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

open class SubcontextImpl[F[_], +A](
  val externalKeys: Set[DIKey],
  val parent: LocatorRef,
  val plan: Plan,
  val functoid: Functoid[A],
  val providedExternals: Map[DIKey, Any],
  val selfKey: DIKey,
) extends Subcontext[F, A] {

  override def provide[T: Tag](value: T)(implicit pos: CodePositionMaterializer): Subcontext[F, A] = {
    val key = DIKey.get[T]
    doAdd(value, pos, key)
  }

  override def provide[T: Tag](name: Identifier)(value: T)(implicit pos: CodePositionMaterializer): Subcontext[F, A] = {
    val key = DIKey[T](name)
    doAdd(value, pos, key)
  }

  override def produce()(implicit F: QuasiIO[F], tagK: TagK[F]): Lifecycle[F, A] = {
    val lookup: PartialFunction[ImportDependency, Any] = {
      case i: ImportDependency if providedExternals.contains(i.target) =>
        providedExternals(i.target)
      case i: ImportDependency if i.target == selfKey =>
        this
    }
    val imported = plan.resolveImports(lookup)
    Injector
      .inherit[F](parent.get)
      .produce(imported)
      .map(_.run(functoid))
  }

  override def produceRun[B](f: A => F[B])(implicit F: QuasiIO[F], tagK: TagK[F]): F[B] = {
    produce().use(f)
  }

  override def unsafeModify[B](f: Functoid[A] => Functoid[B]): Subcontext[F, B] = {
    new SubcontextImpl(externalKeys, parent, plan, f(functoid), providedExternals, selfKey)
  }

  private def doAdd(value: Any, pos: CodePositionMaterializer, key: DIKey): SubcontextImpl[F, A] = {
    if (!externalKeys.contains(key)) {
      throw new UndeclaredKeyException(s"Key $key is not declared as an external key for this local context. The value is provided at ${pos.get.position.toString}", key)
    }

    new SubcontextImpl(
      externalKeys = externalKeys,
      parent = parent,
      plan = plan,
      functoid = functoid,
      providedExternals = providedExternals + (key -> value),
      selfKey = selfKey,
    )
  }

}

object SubcontextImpl {
  def initial[F[_], A](externalKeys: Set[DIKey], parent: LocatorRef, subplan: Plan, functoid: Functoid[A], selfKey: DIKey): SubcontextImpl[F, A] = {
    new SubcontextImpl[F, A](externalKeys, parent, subplan, functoid, Map.empty, selfKey)
  }

  @deprecated("Renamed to initial", "1.2.17")
  def empty[F[_], A](externalKeys: Set[DIKey], locatorRef: LocatorRef, subplan: Plan, impl: Functoid[A], selfKey: DIKey): SubcontextImpl[F, A] = {
    initial(externalKeys, locatorRef, subplan, impl, selfKey)
  }
}
