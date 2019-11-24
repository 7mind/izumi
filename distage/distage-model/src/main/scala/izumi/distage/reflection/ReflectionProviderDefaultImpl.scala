package izumi.distage.reflection

import izumi.distage.model.exceptions.{UnsupportedDefinitionException, UnsupportedWiringException}
import izumi.distage.model.reflection.universe.DIUniverse
import izumi.distage.model.reflection.{DependencyKeyProvider, ReflectionProvider, SymbolIntrospector}
import izumi.fundamentals.reflection.ReflectionUtil
import izumi.fundamentals.reflection.macrortti.LightTypeTag.ReflectionLock

trait ReflectionProviderDefaultImpl extends ReflectionProvider {

  import u.Wiring._
  import u._

  protected def keyProvider: DependencyKeyProvider.Aux[u.type]
  protected def symbolIntrospector: SymbolIntrospector.Aux[u.type]

  override def symbolToWiring(symbl: SafeType): Wiring.PureWiring = ReflectionLock.synchronized {
    symbl match {
      case FactorySymbol(_, factoryMethods, dependencyMethods) =>
        val mw = factoryMethods.map(_.asMethod).map {
          factoryMethod =>
            val factoryMethodSymb = SymbolInfo.Runtime(factoryMethod, symbl, wasGeneric = false)

            val context = DependencyContext.MethodParameterContext(symbl, factoryMethodSymb)

            val resultType: SafeType = keyProvider.resultOfFactoryMethod(context)

            val alreadyInSignature = symbolIntrospector
              .selectNonImplicitParameters(factoryMethod)
              .flatten
              .map(p => keyProvider.keyFromParameter(context, SymbolInfo.Runtime(p, symbl, wasGeneric = false)))

            //val symbolsAlreadyInSignature = alreadyInSignature.map(_.symbol).toSet

            val methodTypeWireable = mkConstructorWiring(resultType)

            val excessiveSymbols = alreadyInSignature.toSet -- methodTypeWireable.requiredKeys

            if (excessiveSymbols.nonEmpty) {
              throw new UnsupportedDefinitionException(
                s"""Augmentation failure.
                   |  * Type $symbl has been considered a factory because of abstract method `$factoryMethodSymb` with result type `$resultType`
                   |  * But method signature contains unrequired symbols: $excessiveSymbols
                   |  * Only the following symbols are requird: ${methodTypeWireable.requiredKeys}
                   |  * This may happen in case you unintentionally bind an abstract type (trait, etc) as implementation type.""".stripMargin, null)
            }

            Wiring.Factory.FactoryMethod(factoryMethodSymb, methodTypeWireable, alreadyInSignature)
        }

        val materials = dependencyMethods.map(methodToAssociation(symbl, _))

        Wiring.Factory(symbl, mw, materials)

      case o =>
        mkConstructorWiring(o)
    }
  }

  override final def constructorParameters(symbl: SafeType): List[Association.Parameter] = ReflectionLock.synchronized {
    constructorParameterLists(symbl).flatten
  }

  override def constructorParameterLists(symbl: SafeType): List[List[Association.Parameter]] = ReflectionLock.synchronized {
    val argLists: List[List[u.SymbolInfo]] = symbolIntrospector.selectConstructor(symbl).map(_.arguments).toList.flatten

    argLists.map(_.map(keyProvider.associationFromParameter))
  }

  private def mkConstructorWiring(symbl: SafeType): SingletonWiring.ReflectiveInstantiationWiring = symbl match {
    case ConcreteSymbol(symb) =>
      SingletonWiring.Constructor(symb, constructorParameters(symb), getPrefix(symb))

    case AbstractSymbol(symb) =>
      SingletonWiring.AbstractSymbol(symb, traitMethods(symb), getPrefix(symb))

    case FactorySymbol(_, _, _) =>
      throw new UnsupportedWiringException(s"Factory cannot produce factories, it's pointless: $symbl", symbl)

    case _ =>
      throw new UnsupportedWiringException(s"Wiring unsupported: $symbl", symbl)
  }

  private def getPrefix(symb: u.SafeType): Option[DIKey] = {
    if (symb.tpe.typeSymbol.isStatic) {
      None
    } else {
      val typeRef = ReflectionUtil.toTypeRef[u.u.type](symb.tpe)
      typeRef
        .map(_.pre)
        .filterNot(m => m.termSymbol.isModule && m.termSymbol.isStatic)
        .map(v => DIKey.TypeKey(SafeType(v)))
    }
  }

  private def traitMethods(symbl: SafeType): Seq[Association.AbstractMethod] = {
    // empty paramLists means parameterless method, List(List()) means nullarg unit method()
    val declaredAbstractMethods = symbl.tpe.members
      .sorted // preserve same order as definition ordering because we implicitly depend on it elsewhere
      .filter(symbolIntrospector.isWireableMethod(symbl.tpe, _))
      .map(_.asMethod)
    declaredAbstractMethods.map(methodToAssociation(symbl, _))
  }

  private def methodToAssociation(symbl: SafeType, method: MethodSymb): Association.AbstractMethod = {
    val methodSymb = SymbolInfo.Runtime(method, symbl, wasGeneric = false)
    val context = DependencyContext.MethodContext(symbl, methodSymb)
    Association.AbstractMethod(context, methodSymb.name, methodSymb.finalResultType, keyProvider.keyFromMethod(context, methodSymb))
  }

  protected object ConcreteSymbol {
    def unapply(arg: SafeType): Option[SafeType] = Some(arg).filter(symbolIntrospector isConcrete _.tpe)
  }

  protected object AbstractSymbol {
    def unapply(arg: SafeType): Option[SafeType] = Some(arg).filter(symbolIntrospector isWireableAbstract _.tpe)
  }

  protected object FactorySymbol {
    def unapply(arg: SafeType): Option[(SafeType, Seq[Symb], Seq[MethodSymb])] =
      Some(arg)
        .filter(symbolIntrospector isFactory _.tpe)
        .map(f => (
          f
          , f.tpe.members.filter(m => symbolIntrospector.isFactoryMethod(f.tpe, m)).toSeq
          , f.tpe.members.filter(m => symbolIntrospector.isWireableMethod(f.tpe, m)).map(_.asMethod).toSeq
        ))
  }

}

object ReflectionProviderDefaultImpl {

  class Runtime
  (
    override val keyProvider: DependencyKeyProvider.Runtime
    , override val symbolIntrospector: SymbolIntrospector.Runtime
  ) extends ReflectionProvider.Runtime
    with ReflectionProviderDefaultImpl

  object Static {
    def apply(macroUniverse: DIUniverse)
             (keyprovider: DependencyKeyProvider.Static[macroUniverse.type]
              , symbolintrospector: SymbolIntrospector.Static[macroUniverse.type]): ReflectionProvider.Static[macroUniverse.type] =
      new ReflectionProviderDefaultImpl {
        override final val u: macroUniverse.type = macroUniverse
        override final val keyProvider: keyprovider.type = keyprovider
        override final val symbolIntrospector: symbolintrospector.type = symbolintrospector
      }
  }

}
