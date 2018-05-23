package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.exceptions.{DIException, UnsupportedWiringException}
import com.github.pshirshov.izumi.distage.model.reflection.universe.DIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.{DependencyKeyProvider, ReflectionProvider, SymbolIntrospector}

trait ReflectionProviderDefaultImpl extends ReflectionProvider {
  import u.Wiring._
  import u._

  protected def keyProvider: DependencyKeyProvider.Aux[u.type]
  protected def symbolIntrospector: SymbolIntrospector.Aux[u.type]

  def symbolToWiring(symbl: TypeFull): Wiring = {
    symbl match {
      case FactorySymbol(_, factoryMethods, dependencyMethods) =>
        val mw = factoryMethods.map(_.asMethod).map {
          factoryMethod =>
            val context = DependencyContext.MethodParameterContext(symbl, SymbolInfo(factoryMethod, symbl))

            val resultType: SafeType = keyProvider.resultOfFactoryMethod(context)

            val alreadyInSignature = symbolIntrospector
              .selectParameters(factoryMethod)
              .map(p => keyProvider.keyFromParameter(context, SymbolInfo(p, symbl)))

            //val symbolsAlreadyInSignature = alreadyInSignature.map(_.symbol).toSet

            val methodTypeWireable = unarySymbolDeps(resultType)

            val excessiveSymbols = alreadyInSignature.toSet -- methodTypeWireable.associations.map(_.wireWith).toSet

            if (excessiveSymbols.nonEmpty) {
              throw new DIException(s"Factory method signature contains symbols which are not required for target product: $excessiveSymbols", null)
            }

            Wiring.FactoryMethod.WithContext(factoryMethod, methodTypeWireable, alreadyInSignature)
        }

        val context = DependencyContext.MethodContext(symbl)
        val materials = dependencyMethods.map {
          method =>
            val methodSymb = SymbolInfo.RuntimeSymbol(method, symbl)
            Association.AbstractMethod(context, methodSymb, keyProvider.keyFromMethod(context, methodSymb))
        }

        Wiring.FactoryMethod(symbl, mw, materials)

      case o =>
        unarySymbolDeps(o)
    }
  }

  override def providerToWiring(function: u.Provider): u.Wiring = {
    val associations = providerParameters(function)
    Wiring.UnaryWiring.Function(function, associations)
  }

  override def constructorParameters(symbl: TypeFull): List[Association.Parameter] = {
    val args: List[u.Symb] = symbolIntrospector.selectConstructor(symbl).arguments

    args.map {
      parameter =>
        val context = DependencyContext.ConstructorParameterContext(symbl)
        val symb = SymbolInfo(parameter, symbl)
        Association.Parameter(context, symb, keyProvider.keyFromParameter(context, symb))
    }
  }

  private def providerParameters(provider: Provider): Seq[Association.Parameter] = {
    val context = DependencyContext.CallableParameterContext(provider)

    provider.diKeys.map(Association.Parameter.fromDIKey(context, _))
  }

  private def unarySymbolDeps(symbl: TypeFull): UnaryWiring.ProductWiring = symbl match {
    case ConcreteSymbol(symb) =>
      UnaryWiring.Constructor(symb, constructorParameters(symb))

    case AbstractSymbol(symb) =>
      UnaryWiring.AbstractSymbol(symb, traitMethods(symb))

    case FactorySymbol(_, _, _) =>
      throw new UnsupportedWiringException(s"Factory cannot produce factories, it's pointless: $symbl", symbl)

    case _ =>
      throw new UnsupportedWiringException(s"Wiring unsupported: $symbl", symbl)
  }

  private def traitMethods(symb: TypeFull): Seq[Association.AbstractMethod] = {
    // empty paramLists means parameterless method, List(List()) means nullarg unit method()
    val declaredAbstractMethods = symb.tpe.members
      .sorted // preserve same order as definition ordering because we implicitly depend on it elsewhere
      .filter(symbolIntrospector.isWireableMethod(symb, _))
      .map(_.asMethod)
    val context = DependencyContext.MethodContext(symb)
    declaredAbstractMethods.map {
      method =>
        val methodSymb = SymbolInfo.RuntimeSymbol(method, symb)
        Association.AbstractMethod(context, methodSymb, keyProvider.keyFromMethod(context, methodSymb))
    }
  }

  protected object ConcreteSymbol {
    def unapply(arg: TypeFull): Option[TypeFull] = Some(arg).filter(symbolIntrospector.isConcrete)
  }

  protected object AbstractSymbol {
    def unapply(arg: TypeFull): Option[TypeFull] = Some(arg).filter(symbolIntrospector.isWireableAbstract)
  }

  protected object FactorySymbol {
    def unapply(arg: TypeFull): Option[(TypeFull, Seq[Symb], Seq[MethodSymb])] =
      Some(arg)
        .filter(symbolIntrospector.isFactory)
        .map(f => (
          f
          , f.tpe.members.filter(m => symbolIntrospector.isFactoryMethod(f, m)).toSeq
          , f.tpe.members.filter(m => symbolIntrospector.isWireableMethod(f, m)).map(_.asMethod).toSeq
        ))
  }
}

object ReflectionProviderDefaultImpl {

  class Runtime(override val keyProvider: DependencyKeyProvider.Runtime
              , override val symbolIntrospector: SymbolIntrospector.Runtime)
    extends ReflectionProvider.Runtime
      with ReflectionProviderDefaultImpl

  object Static {
    def apply(macroUniverse: DIUniverse)
      (keyprovider: DependencyKeyProvider.Static[macroUniverse.type]
       , symbolintrospector: SymbolIntrospector.Static[macroUniverse.type]): ReflectionProvider.Static[macroUniverse.type] =
      new ReflectionProviderDefaultImpl {
        override val u: macroUniverse.type = macroUniverse
        override val keyProvider: keyprovider.type = keyprovider
        override val symbolIntrospector: symbolintrospector.type = symbolintrospector
      }
  }

}
