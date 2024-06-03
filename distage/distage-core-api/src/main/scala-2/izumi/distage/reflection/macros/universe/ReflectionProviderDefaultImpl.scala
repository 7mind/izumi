package izumi.distage.reflection.macros.universe

import izumi.distage.model.definition.With
import izumi.distage.model.exceptions.macros.UnsupportedDefinitionException
import izumi.distage.model.exceptions.reflection.UnsupportedWiringException
import izumi.distage.reflection.macros.universe.basicuniverse.{BaseReflectionProvider, MacroDIKey}
import izumi.distage.reflection.macros.universe.impl.DIUniverse
import izumi.fundamentals.reflection.ReflectionUtil

import scala.annotation.nowarn

@nowarn("msg=outer reference")
trait ReflectionProviderDefaultImpl extends ReflectionProvider {

  import izumi.distage.reflection.macros.universe.basicuniverse.MacroSafeType
  import u.*
  import u.u.Annotation

  private lazy val brp = new BaseReflectionProvider(u.ctx)

  private[this] object With {
    def unapply(ann: Annotation): Option[TypeNative] = {
      ann.tree.tpe.typeArgs.headOption
    }
  }

  def symbolToAnyWiring(tpe: TypeNative): MacroWiring = {
    tpe match {
      case FactorySymbol(symbolMethods, dependencyMethods) =>
        val factoryMethods = symbolMethods.map(_.asMethod).map(factoryMethod(tpe))
        val traitMethods = dependencyMethods.map(methodToAssociation(tpe, _))
        val classParameters = constructorParameterLists(tpe)

        MacroWiring.Factory(factoryMethods, classParameters, traitMethods)

      case _ =>
        mkConstructorWiring(factoryMethod = u.u.NoSymbol, tpe = tpe)
    }
  }

  override def symbolToWiring(tpe: TypeNative): MacroWiring = {
    mkConstructorWiring(factoryMethod = u.u.NoSymbol, tpe = tpe)

  }

  override def constructorParameterLists(tpe: TypeNative): List[List[Association.Parameter]] = {
    selectConstructorArguments(tpe).toList.flatten.map(_.map(parameterToAssociation))
  }

  private[this] def mkConstructorWiring(factoryMethod: SymbNative, tpe: TypeNative): MacroWiring.MacroSingletonWiring = {
    def getPrefix(tpe: TypeNative): Option[MacroDIKey] = {
      if (tpe.typeSymbol.isStatic) {
        None
      } else {
        val typeRef = ReflectionUtil.toTypeRef[u.u.type](tpe)
        typeRef
          .map(_.pre)
          .filterNot(m => m.termSymbol.isModule && m.termSymbol.isStatic)
          .map(v => MacroDIKey.TypeKey(MacroSafeType.create(u.ctx)(v.asInstanceOf[u.ctx.Type])))
      }
    }

    tpe match {
      case ConcreteSymbol(t) =>
        MacroWiring.MacroSingletonWiring.Class(t, constructorParameterLists(t), getPrefix(t))

      case AbstractSymbol(t) =>
        MacroWiring.MacroSingletonWiring.Trait(t, constructorParameterLists(t), traitMethods(t), getPrefix(t))

      case FactorySymbol(mms, _) =>
        throw new UnsupportedWiringException(
          s"""Augmentation failure. Factory cannot produce factories, it's pointless.
             |  * When trying to create an implementation for a factory `${factoryMethod.owner}`
             |  * When trying to create a constructor for the result of `$factoryMethod` - `$tpe`
             |  * Type `${factoryMethod.owner}` has been considered a factory because it's an abstract type and contains unimplemented abstract methods with parameters
             |  * Type `$tpe` has been considered a factory because it's an abstract type and contains unimplemented abstract methods with parameters
             |  * Did you forget a `distage.With` annotation on the factory method to specify a non-abstract implementation type?
             |  * This may happen in case you unintentionally bind an abstract type (trait, etc) as implementation type.
             |
             |  * $mms
             |""".stripMargin,
          MacroSafeType.create(u.ctx)(tpe.asInstanceOf[u.ctx.Type]),
        )

      case _ =>
        val safeType = MacroSafeType.create(u.ctx)(tpe.asInstanceOf[u.ctx.Type])
        val factoryMsg = if (factoryMethod != u.u.NoSymbol) {
          s"""
             |  * When trying to create an implementation for result of `$factoryMethod` of factory `${factoryMethod.owner}`
             |  * Type `${factoryMethod.owner}` has been considered a factory because it's an abstract type and contains unimplemented abstract methods with parameters""".stripMargin
        } else ""
        throw new UnsupportedWiringException(s"Wiring unsupported: `$tpe` / $safeType$factoryMsg", safeType)
    }
  }

  private[this] def resultOfFactoryMethod(symbolInfo: MacroSymbolInfo): TypeNative = {
    symbolInfo.findUniqueAnnotation(typeOfWithAnnotation) match {
      case Some(With(tpe)) =>
        tpe
      case _ =>
        symbolInfo.finalResultType
    }
  }

  private[this] def traitMethods(tpe: TypeNative): List[Association.AbstractMethod] = {
    // empty paramLists means parameterless method, List(List()) means nullarg unit method()
    val declaredAbstractMethods = tpe.members.sorted // preserve same order as definition ordering because we implicitly depend on it elsewhere
      .filter(isWireableMethod)
      .map(_.asMethod)
    declaredAbstractMethods.map(methodToAssociation(tpe, _))
  }

  override def parameterToAssociation(parameterSymbol: MacroSymbolInfo): Association.Parameter = {
    val key = brp.keyFromSymbol(parameterSymbol)
    Association.Parameter(parameterSymbol, brp.tpeFromSymbol(parameterSymbol), key)
  }

  override def zioHasParameters(transformName: String => String)(deepIntersection: List[u.TypeNative]): List[u.Association.Parameter] = {
    deepIntersection.map {
      hasTpe =>
        val tpe = hasTpe.dealias
        val syntheticSymbolInfo = MacroSymbolInfo.Static.syntheticFromType(transformName)(tpe)
        Association.Parameter(syntheticSymbolInfo, brp.tpeFromSymbol(syntheticSymbolInfo), brp.keyFromSymbol(syntheticSymbolInfo))
    }
  }

  private def factoryMethod(tpe: u.TypeNative)(factoryMethod: u.u.MethodSymbol): u.MacroWiring.Factory.FactoryMethod = {
    val factoryMethodSymb = MacroSymbolInfo.Runtime(factoryMethod, tpe, wasGeneric = false)
    val resultType = ReflectionUtil.norm(u.u: u.u.type) {
      resultOfFactoryMethod(factoryMethodSymb)
        .asSeenFrom(tpe, tpe.typeSymbol)
    }

    val alreadyInSignature = factoryMethod.paramLists.flatten.map(symbol => brp.keyFromSymbol(MacroSymbolInfo.Runtime(symbol, tpe, wasGeneric = false)))
    val resultTypeWiring = mkConstructorWiring(factoryMethod, resultType)

    val excessiveTypes = alreadyInSignature.toSet -- resultTypeWiring.requiredKeys
    if (excessiveTypes.nonEmpty) {
      throw new UnsupportedDefinitionException(
        s"""Augmentation failure.
           |  * Type $tpe has been considered a factory because of abstract method `${factoryMethodSymb.name}: ${factoryMethodSymb.typeSignatureInDefiningClass}` with result type `$resultType`
           |  * But method signature contains types not required by constructor of the result type: $excessiveTypes
           |  * Only the following types are required: ${resultTypeWiring.requiredKeys}
           |  * This may happen in case you unintentionally bind an abstract type (trait, etc) as implementation type.""".stripMargin
      )
    }

    MacroWiring.Factory.FactoryMethod(factoryMethodSymb, resultTypeWiring, alreadyInSignature)
  }

  private[this] def methodToAssociation(definingClass: TypeNative, method: MethodSymbNative): Association.AbstractMethod = {
    val methodSymb = MacroSymbolInfo.Runtime(method, definingClass, wasGeneric = false)
    Association.AbstractMethod(methodSymb, brp.tpeFromSymbol(methodSymb), brp.keyFromSymbol(methodSymb))
  }

  private[this] object ConcreteSymbol {
    def unapply(arg: TypeNative): Option[TypeNative] = Some(arg).filter(isConcrete)
  }

  private[this] object AbstractSymbol {
    def unapply(arg: TypeNative): Option[TypeNative] = Some(arg).filter(isWireableAbstract)
  }

  private[this] object FactorySymbol {
    def unapply(arg: TypeNative): Option[(List[SymbNative], List[MethodSymbNative])] = {
      Some(arg)
        .filter(isFactory)
        .map(f => (f.members.filter(isFactoryMethod).toList, f.members.filter(isWireableMethod).map(_.asMethod).toList))
    }
  }

  // symbolintrospector
  private[this] def selectConstructorArguments(tpe: TypeNative): Option[List[List[MacroSymbolInfo]]] = {
    selectConstructorMethod(tpe).map {
      selectedConstructor =>
        val originalParamListTypes = selectedConstructor.paramLists.map(_.map(_.typeSignature))
        val paramLists = selectedConstructor.typeSignatureIn(tpe).paramLists
        // Hack due to .typeSignatureIn throwing out type annotations...
        originalParamListTypes
          .zip(paramLists)
          .map {
            case (origTypes, params) =>
              origTypes.zip(params).map {
                case (o: u.u.AnnotatedTypeApi, p) =>
                  MacroSymbolInfo.Runtime(p, tpe, o.underlying.typeSymbol.isParameter, o.annotations)
                case (o, p) =>
                  MacroSymbolInfo.Runtime(p, tpe, wasGeneric = o.typeSymbol.isParameter)
              }
          }
    }
  }

  def selectConstructorMethod(tpe: TypeNative): Option[MethodSymbNative] = {
    val constructor = findConstructor(tpe)
    if (!constructor.isTerm) {
      None
    } else {
      Some(constructor.asTerm.alternatives.head.asMethod)
    }
  }

  override def isConcrete(tpe: TypeNative): Boolean = {
    tpe match {
      case _: u.u.RefinedTypeApi | u.u.definitions.AnyTpe | u.u.definitions.AnyRefTpe | u.u.definitions.NothingTpe | u.u.definitions.NullTpe =>
        // 1. refinements never have a valid constructor unless they are tautological and can be substituted by a class
        // 2. ignoring non-runtime refinements (type members, covariant overrides) leads to unsoundness
        // rt.parents.size == 1 && !rt.decls.exists(_.isAbstract)
        false

      case _: u.u.SingletonTypeApi =>
        true

      case _ =>
        tpe.typeSymbol.isClass && !tpe.typeSymbol.isAbstract && selectConstructorMethod(tpe).nonEmpty
    }
  }

  override def isWireableAbstract(tpe: TypeNative): Boolean = {
    val abstractMembers = tpe.members.filter(_.isAbstract)

    // no mistake here. Wireable astract is a abstract class or class with an abstract parent having all abstract members wireable
    tpe match {
      case rt: u.u.RefinedTypeApi =>
        val abstractMembers1 = (abstractMembers ++ rt.decls.filter(_.isAbstract)).toSet
        rt.parents.forall {
          pt =>
            !pt.typeSymbol.isFinal && !(pt.typeSymbol.isClass && pt.typeSymbol.asClass.isSealed)
        } && abstractMembers1.forall(isWireableMethod)

      case t =>
        t.typeSymbol.isClass && t.typeSymbol.isAbstract && !t.typeSymbol.asClass.isSealed && abstractMembers.forall(isWireableMethod)
    }
  }

  override def isFactory(tpe: TypeNative): Boolean = {
    !tpe.typeSymbol.isFinal && !(tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isSealed) && {
      val abstracts = tpe.members.filter(_.isAbstract)
      abstracts.nonEmpty && abstracts.forall(m => isFactoryMethod(m))
    }
  }

  private[this] def isWireableMethod(decl: SymbNative): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && decl.owner != u.u.definitions.AnyClass && decl.asMethod.paramLists.isEmpty
  }

  private[this] def isFactoryMethod(decl: SymbNative): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && decl.owner != u.u.definitions.AnyClass
  }

  @inline private[this] def findConstructor(tpe: TypeNative): SymbNative = {
    findConstructor0(tpe).getOrElse(u.u.NoSymbol)
  }

  private[this] def findConstructor0(tpe: TypeNative): Option[SymbNative] = {
    tpe match {
      case intersection: u.u.RefinedTypeApi =>
        intersection.parents.collectFirst(Function.unlift(findConstructor0))
      case tpe =>
        if (tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isTrait) {
          tpe.baseClasses.collectFirst(Function.unlift(b => if (b ne tpe.typeSymbol) findConstructor0(b.typeSignature) else None))
        } else {
          tpe.decl(u.u.termNames.CONSTRUCTOR).alternatives.find(_.isPublic)
        }
    }
  }

  protected def typeOfWithAnnotation: TypeNative
}

object ReflectionProviderDefaultImpl {
  def apply(macroUniverse: DIUniverse): ReflectionProvider.Aux[macroUniverse.type] = {
    new ReflectionProviderDefaultImpl {
      override final val u: macroUniverse.type = macroUniverse
      override protected val typeOfWithAnnotation: u.TypeNative = u.u.typeOf[With[Any]]
    }
  }
}
