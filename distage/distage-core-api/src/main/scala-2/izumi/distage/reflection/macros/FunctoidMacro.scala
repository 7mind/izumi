package izumi.distage.reflection.macros

import izumi.distage.constructors.DebugProperties
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.Provider
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.reflection.macros.universe.StaticDIUniverse.Aux
import izumi.distage.reflection.macros.universe.{DIUniverseLiftables, ReflectionProviderDefaultImpl, StaticDIUniverse}
import izumi.fundamentals.reflection.TrivialMacroLogger

import scala.annotation.nowarn
import scala.reflect.macros.blackbox

/**
  * To see macro debug output during compilation, set `-Dizumi.debug.macro.distage.functoid=true` java property!
  *
  * {{{
  *   sbt -Dizumi.debug.macro.distage.functoid=true compile
  * }}}
  *
  * @see [[izumi.distage.constructors.DebugProperties]]
  */
class FunctoidMacro(val c: blackbox.Context) {
  final val macroUniverse: Aux[c.universe.type] = StaticDIUniverse(c)
  type Parameter = macroUniverse.Association.CompactParameter

  private final val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.functoid`.name)
  private final val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)

  import c.universe.*

  def impl[R: c.WeakTypeTag](fun: Tree): c.Expr[Functoid[R]] = {
    val associations = analyze(fun, weakTypeOf[R])
    val result = generateProvider[R](associations, fun)

    logger.log(
      s"""DIKeyWrappedFunction info:
         | Symbol: ${fun.symbol}\n
         | IsMethodSymbol: ${Option(fun.symbol).exists(_.isMethod)}\n
         | Extracted Annotations: ${associations.flatMap(_.symbol.friendlyAnnotations)}\n
         | Extracted DIKeys: ${associations.map(_.key)}\n
         | argument: ${showCode(fun)}\n
         | argumentTree: ${showRaw(fun)}\n
         | argumentType: ${fun.tpe}
         | Result code: ${showCode(result.tree)}""".stripMargin
    )

    result
  }

  def analyze(tree: Tree, ret: Type): List[Parameter] = tree match {
    case Block(List(), inner) =>
      analyze(inner, ret)
    case Function(args, body) =>
      analyzeMethodRef(args.map(_.symbol), body)
    case _ if tree.tpe ne null =>
      if (tree.tpe.typeSymbol.isModuleClass) {
        val functionNClasses = definitions.FunctionClass.seq.toSet[Symbol]
        val overridenFunctionNApply = tree.tpe.typeSymbol.info
          .decl(TermName("apply")).alternatives
          .find(_.overrides.exists(functionNClasses contains _.owner))

        overridenFunctionNApply.fold(analyzeValRef(tree.tpe)) {
          method => analyzeMethodRef(extractMethodReferenceParams(method), tree)
        }
      } else {
        analyzeValRef(tree.tpe)
      }
    case _ =>
      c.abort(
        tree.pos,
        s"""
           | Can handle only method references of form (method _) or lambda bodies of form (args => body).\n
           | Argument doesn't seem to be a method reference or a lambda:\n
           |   argument: ${showCode(tree)}\n
           |   argumentTree: ${showRaw(tree)}\n
           | Hint: Try appending _ to your method name""".stripMargin,
      )
  }

  def generateProvider[R: c.WeakTypeTag](parameters: List[Parameter], fun: Tree): c.Expr[Functoid[R]] = {
    val tools = DIUniverseLiftables(macroUniverse)
    import tools.{liftTypeToSafeType, liftableCompactParameter}

    val seqName = if (parameters.nonEmpty) TermName(c.freshName("seqAny")) else TermName("_")

    val casts = parameters.indices.map(i => q"$seqName($i)")
    val parametersNoByName = Liftable.liftList[Parameter].apply(parameters)

//    val parametersNoByName = q"???"

    c.Expr[Functoid[R]] {
      q"""{
        val fun = $fun

        new ${weakTypeOf[Functoid[R]]}(
          new ${weakTypeOf[Provider.ProviderImpl[R]]}(
            $parametersNoByName,
            ${liftTypeToSafeType(weakTypeOf[R])},
            fun,
            { ($seqName: _root_.scala.Seq[_root_.scala.Any]) => fun.asInstanceOf[(..${casts.map(_ => definitions.AnyTpe)}) => ${definitions.AnyTpe}](..$casts) },
            ${symbolOf[ProviderType.Function.type].asClass.module},
          )
        )
      }"""
    }
  }

  protected[this] def analyzeMethodRef(lambdaArgs: List[Symbol], body: Tree): List[Parameter] = {
    def association(p: Symbol): Parameter = {
      reflectionProvider.parameterToAssociation2(macroUniverse.MacroSymbolInfo.Runtime(p))
    }

    val lambdaParams = lambdaArgs.map(association)
    val methodReferenceParams = body match {
      case Apply(f, args) if args.map(_.symbol) == lambdaArgs =>
        logger.log(s"Matched function body as a method reference - consists of a single call to a function $f with the same arguments as lambda- ${showRaw(body)}")

        extractMethodReferenceParams(f.symbol).map(association)
      case _ =>
        logger.log(s"Function body didn't match as a variable or a method reference - ${showRaw(body)}")

        Nil
    }

    logger.log(s"lambda params: $lambdaParams")
    logger.log(s"method ref params: $methodReferenceParams")

    @nowarn("msg=Unused import")
    val annotationsOnMethodAreNonEmptyAndASuperset: Boolean = {
      import scala.collection.compat._
      methodReferenceParams.sizeCompare(lambdaParams) == 0 &&
      methodReferenceParams.exists(_.symbol.friendlyAnnotations.nonEmpty)
    }

//    // this is somewhat superfluous since normally lambda parameters can't be annotated in source code at all
//    val annotationsOnMethodAreNonEmptyAndASuperset: Boolean = {
//      val annotationsOnLambdaParamSymbols = lambdaParams.iterator.map(_.symbol.annotations)
//      val annotationsOnMethod = Predef.wrapRefArray(methodReferenceParams.iterator.map(_.symbol.annotations).toArray)
//
//      annotationsOnMethod.exists(_.nonEmpty) &&
//      annotationsOnLambdaParamSymbols.zipAll(annotationsOnMethod.iterator, null, null).forall {
//        case (null, _) => false
//        case (_, null) => false
//        case (left, right) =>
//          left.iterator.zipAll(right.iterator, null, null).forall {
//            case (l, r) => (l eq null) || l == r
//          }
//      }
//    }

    // if method reference has more annotations, get parameters from reference instead
    // to preserve annotations!
    if (annotationsOnMethodAreNonEmptyAndASuperset) {
      // Use types from the generated lambda, not the method reference, because method reference types maybe generic/unresolved/unrelated
      // But lambda params should be sufficiently 'grounded' at this point
      // (Besides, lambda types are the ones specified by the caller, we should respect them)
      methodReferenceParams.zip(lambdaParams).map {
        case (mArg, lArg) =>
          mArg.copy(
            symbol = lArg.symbol.withFriendlyAnnotations(mArg.symbol.friendlyAnnotations),
            key = mArg.key.withTpe(lArg.stpe),
          )
      }
    } else {
      lambdaParams
    }
  }

  protected[this] def extractMethodReferenceParams(symbol: Symbol): List[Symbol] = {
    val isSyntheticCaseClassApply = {
      symbol.name.decodedName.toString == "apply" &&
      symbol.isSynthetic &&
      symbol.owner.companion.isClass &&
      symbol.owner.companion.asClass.isCaseClass
    }

    val method = if (isSyntheticCaseClassApply) {
      // since this is a _synthetic_ apply, its signature must match the case class constructor exactly, so we don't check it
      val constructor = symbol.owner.companion.asClass.primaryConstructor
      logger.log(s"Matched method reference as a synthetic apply corresponding to primary constructor $constructor")
      constructor
    } else {
      symbol.asMethod
    }

    method.typeSignature.paramLists.flatten
  }

  protected[this] def analyzeValRef(sig: Type): List[Parameter] = {
    widenFunctionObject(sig).typeArgs.init.map {
      tpe =>
        val symbol = macroUniverse.MacroSymbolInfo.Static.syntheticFromType(c.freshName)(tpe)
        reflectionProvider.parameterToAssociation2(symbol)
    }
  }

  protected[this] def widenFunctionObject(sig: Type): Type = {
    (sig match {
      case s: SingleTypeApi =>
        sig.baseType(s.sym.typeSignature.baseClasses.find(definitions.FunctionClass.seq.contains(_)).get)
      case _ =>
        sig
    }): @nowarn("msg=outer reference")
  }

}
