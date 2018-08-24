package com.github.pshirshov.izumi.distage.model.reflection.macros

import com.github.pshirshov.izumi.distage.model.reflection.universe._

abstract class DIUniverseLiftables[D <: StaticDIUniverse](val u: D) {

  import u._
  import u.u._

  implicit val liftableRuntimeUniverse: Liftable[RuntimeDIUniverse.type] =
    { _: RuntimeDIUniverse.type => q"${symbolOf[RuntimeDIUniverse.type].asClass.module}" }

  implicit def liftableSafeType: Liftable[SafeType]

  protected final val liftableDefaultSafeType: Liftable[SafeType] =
    value => q"{ $RuntimeDIUniverse.SafeType.get[${Liftable.liftType(value.tpe)}] }"

  /** A hack to support generic methods in macro factories, see `WeakTag`, `GenericAssistedFactory` and associated tests **/
  protected final val liftableUnsafeWeakSafeType: Liftable[SafeType] =
    value => q"{ $RuntimeDIUniverse.SafeType.unsafeGetWeak[${Liftable.liftType(value.tpe)}] }"

  // DIKey

  implicit val liftableTypeKey: Liftable[DIKey.TypeKey] = {
    case DIKey.TypeKey(symbol) => q"""
    { new $RuntimeDIUniverse.DIKey.TypeKey($symbol) }
      """
  }

  implicit val liftableIdKey: Liftable[DIKey.IdKey[_]] = {
    case idKey: DIKey.IdKey[_] =>
      import idKey._
      val lift = idContract.asInstanceOf[IdContractImpl[Any]].liftable
      q"""{ new $RuntimeDIUniverse.DIKey.IdKey($tpe, ${lift(id)}) }"""
  }

  implicit val liftableProxyElementKey: Liftable[DIKey.ProxyElementKey] = {
    case DIKey.ProxyElementKey(proxied, symbol) => q"""
    { new $RuntimeDIUniverse.DIKey.ProxyElementKey(${liftableDIKey(proxied)}, $symbol) }
      """
  }

  implicit val liftableSetElementKey: Liftable[DIKey.SetElementKey] = {
    case DIKey.SetElementKey(set, index, symbol) => q"""
    { new $RuntimeDIUniverse.DIKey.SetElementKey(${liftableDIKey(set)}, $index, $symbol) }
      """
  }

  implicit val liftableDIKey: Liftable[DIKey] = {
    Liftable[DIKey] {
      d => (d: @unchecked) match {
        case t: DIKey.TypeKey => q"$t"
        case i: DIKey.IdKey[_] => q"${liftableIdKey(i)}"
        case p: DIKey.ProxyElementKey => q"$p"
        case s: DIKey.SetElementKey => q"$s"
      }
    }
  }

  // ParameterContext

  implicit val liftableConstructorParameterContext: Liftable[DependencyContext.ConstructorParameterContext] = {
    context => q"{ new $RuntimeDIUniverse.DependencyContext.ConstructorParameterContext(${context.definingClass}, ${context.parameterSymbol}) }"
  }

  implicit val liftableMethodParameterContext: Liftable[DependencyContext.MethodParameterContext] = {
    context => q"{ new $RuntimeDIUniverse.DependencyContext.MethodParameterContext(${context.definingClass}, ${context.factoryMethod}) }"
  }

  implicit val liftableParameterContext: Liftable[DependencyContext.ParameterContext] = {
    case context: DependencyContext.ConstructorParameterContext => q"$context"
    case context: DependencyContext.MethodParameterContext => q"$context"
  }

  // SymbolInfo

  // Symbols may contain uninstantiated poly types, and are usually only included for debugging purposes anyway
  // so weak types are allowed here (See Inject config tests in StaticInjectorTest, they do break if this is changed)
  implicit val liftableSymbolInfo: Liftable[SymbolInfo] = {
    info => q"""
    { $RuntimeDIUniverse.SymbolInfo.Static(${info.name}, ${liftableUnsafeWeakSafeType(info.finalResultType)}, ${info.annotations}, ${liftableUnsafeWeakSafeType(info.definingClass)}) }
       """
  }

  // Associations

  implicit val liftableParameter: Liftable[Association.Parameter] = {
    case Association.Parameter(context, name, tpe, wireWith) =>
      q"{ new $RuntimeDIUniverse.Association.Parameter($context, $name, $tpe, $wireWith)}"
    }

  // Annotations

  case class TreeLiteral(tree: Tree)

  implicit val liftableLiteralTree: Liftable[TreeLiteral] = {
    case TreeLiteral(Literal(c: Constant)) => q"""{
      _root_.scala.reflect.runtime.universe.Literal(_root_.scala.reflect.runtime.universe.Constant($c))
      }"""
    case TreeLiteral(tree) => q"""{
      import _root_.scala.reflect.runtime.universe._

      _root_.scala.StringContext(${showCode(tree, printRootPkg = true)}).q.apply()
      }"""
  }

  implicit val liftableAnnotation: Liftable[Annotation] = {
    case Annotation(tpe, args, _) => q"""{
    _root_.scala.reflect.runtime.universe.Annotation.apply(${SafeType(tpe)}.tpe, ${args.map(TreeLiteral)}, _root_.scala.collection.immutable.ListMap.empty)
    }"""
  }

}

object DIUniverseLiftables {
  def apply(u: StaticDIUniverse): DIUniverseLiftables[u.type] = new DIUniverseLiftables[u.type](u) {
    override implicit val liftableSafeType: u.u.Liftable[u.SafeType] = liftableDefaultSafeType
  }

  def generateUnsafeWeakSafeTypes(u: StaticDIUniverse): DIUniverseLiftables[u.type] = new DIUniverseLiftables[u.type](u) {
    override implicit val liftableSafeType: u.u.Liftable[u.SafeType] = liftableUnsafeWeakSafeType
  }
}
