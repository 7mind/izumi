package com.github.pshirshov.izumi.distage.model.reflection.macros

import com.github.pshirshov.izumi.distage.model.reflection.universe._

class DIUniverseLiftables[D <: StaticDIUniverse](val u: D) {

  import u._
  import u.u._

  implicit final val liftableRuntimeUniverse: Liftable[RuntimeDIUniverse.type] =
    { _: RuntimeDIUniverse.type => q"${symbolOf[RuntimeDIUniverse.type].asClass.module}" }

  implicit final val liftableSafeType: Liftable[SafeType] =
    value => q"{ $RuntimeDIUniverse.SafeType.getWeak[${Liftable.liftType(value.tpe)}] }"

  // DIKey

  implicit final val liftableTypeKey: Liftable[DIKey.TypeKey] = {
    case DIKey.TypeKey(symbol) => q"""
    { new $RuntimeDIUniverse.DIKey.TypeKey($symbol) }
      """
  }

  implicit final val liftableIdKey: Liftable[DIKey.IdKey[_]] = {
    case idKey: DIKey.IdKey[_] =>
      import idKey._
      val lift = idContract.asInstanceOf[IdContractImpl[Any]].liftable
      q"""{ new $RuntimeDIUniverse.DIKey.IdKey($tpe, ${lift(id)}) }"""
  }

  implicit final val liftableProxyElementKey: Liftable[DIKey.ProxyElementKey] = {
    case DIKey.ProxyElementKey(proxied, symbol) => q"""
    { new $RuntimeDIUniverse.DIKey.ProxyElementKey(${liftableDIKey(proxied)}, $symbol) }
      """
  }

  implicit final val liftableSetElementKey: Liftable[DIKey.SetElementKey] = {
    case DIKey.SetElementKey(set, index, symbol) => q"""
    { new $RuntimeDIUniverse.DIKey.SetElementKey(${liftableDIKey(set)}, $index, $symbol) }
      """
  }

  implicit final val liftableDIKey: Liftable[DIKey] = {
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

  implicit final val liftableConstructorParameterContext: Liftable[DependencyContext.ConstructorParameterContext] = {
    context => q"{ new $RuntimeDIUniverse.DependencyContext.ConstructorParameterContext(${context.definingClass}, ${context.parameterSymbol}) }"
  }

  implicit final val liftableMethodParameterContext: Liftable[DependencyContext.MethodParameterContext] = {
    context => q"{ new $RuntimeDIUniverse.DependencyContext.MethodParameterContext(${context.definingClass}, ${context.factoryMethod}) }"
  }

  implicit final val liftableParameterContext: Liftable[DependencyContext.ParameterContext] = {
    case context: DependencyContext.ConstructorParameterContext => q"$context"
    case context: DependencyContext.MethodParameterContext => q"$context"
  }

  // SymbolInfo

  implicit final val liftableSymbolInfo: Liftable[SymbolInfo] = {
    info => q"""
    { $RuntimeDIUniverse.SymbolInfo.Static(${info.name}, ${info.finalResultType}, ${info.annotations}, ${info.definingClass}) }
       """
  }

  // Associations

  implicit final val liftableParameter: Liftable[Association.Parameter] = {
    case Association.Parameter(context, name, tpe, wireWith) =>
      q"{ new $RuntimeDIUniverse.Association.Parameter($context, $name, $tpe, $wireWith)}"
    }

  // Annotations

  case class TreeLiteral(tree: Tree)

  implicit final val liftableLiteralTree: Liftable[TreeLiteral] = {
    case TreeLiteral(Literal(c: Constant)) => q"""{
      _root_.scala.reflect.runtime.universe.Literal(_root_.scala.reflect.runtime.universe.Constant($c))
      }"""
    case TreeLiteral(tree) => q"""{
      import _root_.scala.reflect.runtime.universe._

      _root_.scala.StringContext(${showCode(tree, printRootPkg = true)}).q.apply()
      }"""
  }

  implicit final val liftableAnnotation: Liftable[Annotation] = {
    case Annotation(tpe, args, _) => q"""{
    _root_.scala.reflect.runtime.universe.Annotation.apply(${SafeType(tpe)}.tpe, ${args.map(TreeLiteral)}, _root_.scala.collection.immutable.ListMap.empty)
    }"""
  }

}

object DIUniverseLiftables {
  def apply(u: StaticDIUniverse): DIUniverseLiftables[u.type] = new DIUniverseLiftables[u.type](u)
}
