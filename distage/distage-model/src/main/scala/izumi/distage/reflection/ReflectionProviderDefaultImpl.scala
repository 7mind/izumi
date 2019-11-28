package izumi.distage.reflection

import izumi.distage.model.definition.{Id, With}
import izumi.distage.model.exceptions.{BadIdAnnotationException, UnsupportedDefinitionException, UnsupportedWiringException}
import izumi.distage.model.reflection.ReflectionProvider
import izumi.distage.model.reflection.universe.DIUniverse
import izumi.fundamentals.reflection.ReflectionUtil
import izumi.fundamentals.reflection.macrortti.LightTypeTag.ReflectionLock

trait ReflectionProviderDefaultImpl extends ReflectionProvider {
  self =>

  import u._

  // dependencykeyprovider
  override def keyFromParameter(context: DependencyContext.ParameterContext, parameterSymbol: SymbolInfo): DIKey.BasicKey = {
    val typeKey = if (parameterSymbol.isByName) {
      DIKey.TypeKey(SafeType(parameterSymbol.finalResultType.use(_.typeArgs.head.finalResultType)))
    } else {
      DIKey.TypeKey(parameterSymbol.finalResultType)
    }

    withOptionalName(parameterSymbol, typeKey)
  }

  override def associationFromParameter(parameterSymbol: u.SymbolInfo): u.Association.Parameter = {
    val context = DependencyContext.ConstructorParameterContext(parameterSymbol.definingClass, parameterSymbol)

    Association.Parameter(
      context
      , parameterSymbol.name
      , parameterSymbol.finalResultType
      , keyFromParameter(context, parameterSymbol)
      , parameterSymbol.isByName
      , parameterSymbol.wasGeneric
    )
  }

  override def keyFromMethod(context: DependencyContext.MethodContext, methodSymbol: SymbolInfo): DIKey.BasicKey = {
    val typeKey = DIKey.TypeKey(methodSymbol.finalResultType)
    withOptionalName(methodSymbol, typeKey)
  }

  private[this] def withOptionalName(parameterSymbol: SymbolInfo, typeKey: DIKey.TypeKey): u.DIKey.BasicKey =
    findSymbolAnnotation(typeOfIdAnnotation, parameterSymbol) match {
      case Some(Id(name)) =>
        typeKey.named(name)
      case Some(v) =>
        throw new BadIdAnnotationException(typeOfIdAnnotation.toString, v)
      case _ =>
        typeKey
    }

  // reflectionprovider

  import u.Wiring._
  import u._

  override def symbolToWiring(tpe: u.TypeNative): Wiring.PureWiring = ReflectionLock.synchronized {
    tpe match {
      case FactorySymbol(_, factoryMethods, dependencyMethods) =>
        val unsafeSafeType = SafeType(tpe)

        val mw = factoryMethods.map(_.asMethod).map {
          factoryMethod =>
            val factoryMethodSymb = SymbolInfo.Runtime(factoryMethod, unsafeSafeType, wasGeneric = false)

            val context = DependencyContext.MethodParameterContext(unsafeSafeType, factoryMethodSymb)

            val resultType = resultOfFactoryMethod(context)

            val alreadyInSignature = self
              .selectNonImplicitParameters(factoryMethod)
              .flatten
              .map(p => keyFromParameter(context, SymbolInfo.Runtime(p, unsafeSafeType, wasGeneric = false)))

            //val symbolsAlreadyInSignature = alreadyInSignature.map(_.symbol).toSet

            val methodTypeWireable = mkConstructorWiring(resultType)

            val excessiveSymbols = alreadyInSignature.toSet -- methodTypeWireable.requiredKeys

            if (excessiveSymbols.nonEmpty) {
              throw new UnsupportedDefinitionException(
                s"""Augmentation failure.
                   |  * Type $tpe has been considered a factory because of abstract method `$factoryMethodSymb` with result type `$resultType`
                   |  * But method signature contains unrequired symbols: $excessiveSymbols
                   |  * Only the following symbols are requird: ${methodTypeWireable.requiredKeys}
                   |  * This may happen in case you unintentionally bind an abstract type (trait, etc) as implementation type.""".stripMargin, null)
            }

            Wiring.Factory.FactoryMethod(factoryMethodSymb, methodTypeWireable, alreadyInSignature)
        }

        val materials = dependencyMethods.map(methodToAssociation(tpe, _))

        Wiring.Factory(unsafeSafeType, mw, materials)

      case o =>
        mkConstructorWiring(o)
    }
  }

  override def constructorParameterLists(symbl: TypeNative): List[List[Association.Parameter]] = ReflectionLock.synchronized {
    val argLists: List[List[u.SymbolInfo]] = selectConstructor(symbl).map(_.arguments).toList.flatten
    argLists.map(_.map(associationFromParameter))
  }

  private[this] def mkConstructorWiring(symbl: TypeNative): SingletonWiring.ReflectiveInstantiationWiring = symbl match {
    case ConcreteSymbol(symb) =>
      SingletonWiring.Constructor(SafeType(symb), constructorParameters(symb), getPrefix(symb))

    case AbstractSymbol(symb) =>
      SingletonWiring.AbstractSymbol(SafeType(symb), traitMethods(symb), getPrefix(symb))

    case FactorySymbol(_, _, _) =>
      throw new UnsupportedWiringException(s"Factory cannot produce factories, it's pointless: $symbl", SafeType(symbl))

    case _ =>
      throw new UnsupportedWiringException(s"Wiring unsupported: $symbl", SafeType(symbl))
  }

  private[this] def constructorParameters(symbl: u.TypeNative): List[Association.Parameter] = {
    ReflectionLock.synchronized {
      constructorParameterLists(symbl).flatten
    }
  }

  private[this] def getPrefix(symb: u.TypeNative): Option[DIKey] = {
    if (symb.typeSymbol.isStatic) {
      None
    } else {
      val typeRef = ReflectionUtil.toTypeRef[u.u.type](symb)
      typeRef
        .map(_.pre)
        .filterNot(m => m.termSymbol.isModule && m.termSymbol.isStatic)
        .map(v => DIKey.TypeKey(SafeType(v)))
    }
  }

  private[this] def resultOfFactoryMethod(context: u.DependencyContext.MethodParameterContext): u.TypeNative = {
    context.factoryMethod.findUniqueAnnotation(typeOfWithAnnotation) match {
      case Some(With(tpe)) =>
        tpe
      case _ =>
        context.factoryMethod.finalResultType.use(identity)
    }
  }

  private[this] def traitMethods(symbl: TypeNative): Seq[Association.AbstractMethod] = {
    // empty paramLists means parameterless method, List(List()) means nullarg unit method()
    val declaredAbstractMethods = symbl.members
      .sorted // preserve same order as definition ordering because we implicitly depend on it elsewhere
      .filter(isWireableMethod(symbl, _))
      .map(_.asMethod)
    declaredAbstractMethods.map(methodToAssociation(symbl, _))
  }

  private[this] def methodToAssociation(symbl: u.TypeNative, method: MethodSymbNative): Association.AbstractMethod = {
    val unsafeSafeType = SafeType(symbl)
    val methodSymb = SymbolInfo.Runtime(method, unsafeSafeType, wasGeneric = false)
    val context = DependencyContext.MethodContext(unsafeSafeType, methodSymb)
    Association.AbstractMethod(context, methodSymb.name, methodSymb.finalResultType, keyFromMethod(context, methodSymb))
  }

  private object ConcreteSymbol {
    def unapply(arg: TypeNative): Option[TypeNative] = Some(arg).filter(isConcrete)
  }

  private object AbstractSymbol {
    def unapply(arg: TypeNative): Option[TypeNative] = Some(arg).filter(isWireableAbstract)
  }

  private object FactorySymbol {
    def unapply(arg: TypeNative): Option[(TypeNative, Seq[SymbNative], Seq[MethodSymbNative])] =
      Some(arg)
        .filter(isFactory)
        .map(f => (
          f
          , f.members.filter(m => isFactoryMethod(f, m)).toSeq
          , f.members.filter(m => isWireableMethod(f, m)).map(_.asMethod).toSeq
        ))
  }

  // symbolintrospector
  override def selectConstructor(tpe: u.TypeNative): Option[SelectedConstructor] = ReflectionLock.synchronized {
    selectConstructorMethod(tpe).map {
      selectedConstructor =>
        val originalParamListTypes = selectedConstructor.paramLists.map(_.map(_.typeSignature))
        val paramLists = selectedConstructor.typeSignatureIn(tpe).paramLists
        // Hack due to .typeSignatureIn throwing out type annotations...
        val paramsWithAnnos = originalParamListTypes
          .zip(paramLists)
          .map {
            case (origTypes, params) =>
              origTypes.zip(params).map {
                case (o: u.u.AnnotatedTypeApi, p) =>
                  u.SymbolInfo.Runtime(p, SafeType(tpe), o.underlying.typeSymbol.isParameter, o.annotations)
                case (o, p) =>
                  u.SymbolInfo.Runtime(p, SafeType(tpe), o.typeSymbol.isParameter)
              }
          }
        SelectedConstructor(selectedConstructor, paramsWithAnnos)
    }
  }

  private[this] def selectConstructorMethod(tpe: u.TypeNative): Option[u.MethodSymbNative] = ReflectionLock.synchronized {
    val constructor = findConstructor(tpe)
    if (!constructor.isTerm) {
      None
    } else {
      Some(constructor.asTerm.alternatives.head.asMethod)
    }
  }

  override def selectNonImplicitParameters(symb: u.MethodSymbNative): List[List[u.SymbNative]] = ReflectionLock.synchronized {
    symb.paramLists.takeWhile(_.headOption.forall(!_.isImplicit))
  }

  override def isConcrete(tpe: u.TypeNative): Boolean = {
    tpe match {
      case _: u.u.RefinedTypeApi | _: u.u.definitions.AnyTpe.type     | _: u.u.definitions.AnyRefTpe.type
                                 | _: u.u.definitions.NothingTpe.type | _: u.u.definitions.NullTpe.type =>
        // 1. refinements never have a valid constructor unless they are tautological and can be substituted by a class
        // 2. ignoring non-runtime refinements (type members, covariant overrides) leads to unsoundness
        // rt.parents.size == 1 && !rt.decls.exists(_.isAbstract)
        false

      case _ =>
        tpe.typeSymbol.isClass && !tpe.typeSymbol.isAbstract && selectConstructorMethod(tpe).nonEmpty
    }
  }

  override def isWireableAbstract(tpe: u.TypeNative): Boolean = ReflectionLock.synchronized {
    val abstractMembers = tpe.members.filter(_.isAbstract)

    // no mistake here. Wireable astract is a abstract class or class with an abstract parent having all abstract members wireable
    tpe match {
      case rt: u.u.RefinedTypeApi =>
        val abstractMembers1 = (abstractMembers ++ rt.decls.filter(_.isAbstract)).toSet
        rt.parents.forall(!_.typeSymbol.isFinal) && abstractMembers1.forall(m => isWireableMethod(tpe, m))

      case t =>
        t.typeSymbol.isClass && t.typeSymbol.isAbstract && abstractMembers.forall(m => isWireableMethod(tpe, m))
    }
  }

  override def isFactory(tpe: u.TypeNative): Boolean = ReflectionLock.synchronized {
    tpe.typeSymbol.isClass && tpe.typeSymbol.isAbstract && {
      val abstracts = tpe.members.filter(_.isAbstract)
      abstracts.exists(isFactoryMethod(tpe, _)) &&
        abstracts.forall(m => isFactoryMethod(tpe, m) || isWireableMethod(tpe, m))
    }
  }

  override def isWireableMethod(tpe: u.TypeNative, decl: u.SymbNative): Boolean = ReflectionLock.synchronized {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      decl.asMethod.paramLists.isEmpty && u.SafeType(decl.asMethod.returnType) != tpe
    }
  }

  override def isFactoryMethod(tpe: u.TypeNative, decl: u.SymbNative): Boolean = ReflectionLock.synchronized {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      val paramLists = decl.asMethod.paramLists
      paramLists.nonEmpty && paramLists.forall { list =>
        !list.exists(_.typeSignature =:= decl.asMethod.returnType) && !list.exists(_.typeSignature =:= tpe)
      }
    }
  }

  override def findSymbolAnnotation(annType: u.TypeNative, symb: u.SymbolInfo): Option[u.u.Annotation] = ReflectionLock.synchronized {
    symb.findUniqueAnnotation(annType)
  }

  private[this] def findConstructor(tpe: u.TypeNative): u.u.Symbol = {
    tpe.decl(u.u.termNames.CONSTRUCTOR)
  }

  protected def typeOfWithAnnotation: u.TypeNative
  protected def typeOfIdAnnotation: u.TypeNative
}

object ReflectionProviderDefaultImpl {
  object Static {
    def apply(macroUniverse: DIUniverse): ReflectionProvider.Aux[macroUniverse.type] = {
      new ReflectionProviderDefaultImpl {
        override final val u: macroUniverse.type = macroUniverse

        override protected val typeOfIdAnnotation: u.TypeNative = u.u.typeOf[Id]
        override protected val typeOfWithAnnotation: u.TypeNative = u.u.typeOf[With[Any]]
      }
    }
  }
}

