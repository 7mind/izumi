package izumi.distage.model.reflection.macros

import izumi.distage.constructors.DebugProperties
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.Provider
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.model.reflection.universe.StaticDIUniverse.Aux
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.TrivialMacroLogger

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

  private final val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.functoid`.name)
  private final val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)

  import c.universe._
  import macroUniverse._

  case class ExtractedInfo(associations: List[Association.Parameter], isValReference: Boolean)

  def impl[R: c.WeakTypeTag](fun: Tree): c.Expr[Functoid[R]] = {
    val associations = analyze(fun, weakTypeOf[R])
    val result = generateProvider[R](associations, fun)

    logger.log(
      s"""DIKeyWrappedFunction info:
         | Symbol: ${fun.symbol}\n
         | IsMethodSymbol: ${Option(fun.symbol).exists(_.isMethod)}\n
         | Extracted Annotations: ${associations.flatMap(_.symbol.annotations)}\n
         | Extracted DIKeys: ${associations.map(_.key)}\n
         | argument: ${showCode(fun)}\n
         | argumentTree: ${showRaw(fun)}\n
         | argumentType: ${fun.tpe}
         | Result code: ${showCode(result.tree)}""".stripMargin
    )

    result
  }

  def analyze(tree: Tree, ret: Type): List[Association.Parameter] = tree match {
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

  def generateProvider[R: c.WeakTypeTag](parameters: List[Association.Parameter], fun: Tree): c.Expr[Functoid[R]] = {
    val tools = DIUniverseLiftables(macroUniverse)
    import tools.{liftTypeToSafeType, liftableParameter}

    val seqName = if (parameters.nonEmpty) TermName(c.freshName("seqAny")) else TermName("_")

    val casts = parameters.indices.map(i => q"$seqName($i)")
    val parametersNoByName = Liftable.liftList[Association.Parameter].apply(parameters)

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

  protected[this] def analyzeMethodRef(lambdaArgs: List[Symbol], body: Tree): List[Association.Parameter] = {
    def association(p: Symbol): Association.Parameter = {
      reflectionProvider.parameterToAssociation(SymbolInfo.Runtime(p))
    }

    val lambdaParams = lambdaArgs.map(association)
    val methodReferenceParams = body match {
      case Apply(f, _) =>
        logger.log(s"Matched function body as a method reference - consists of a single call to a function $f - ${showRaw(body)}")

        extractMethodReferenceParams(f.symbol).map(association)
      case _ =>
        logger.log(s"Function body didn't match as a variable or a method reference - ${showRaw(body)}")

        Nil
    }

    logger.log(s"lambda params: $lambdaParams")
    logger.log(s"method ref params: $methodReferenceParams")

    def annotationsOnMethodAreNonEmptyAndASuperset: Boolean = {
      val annotationsOnLambda = lambdaParams.iterator.map(_.symbol.annotations)
      val annotationsOnMethod = Predef.wrapRefArray(methodReferenceParams.iterator.map(_.symbol.annotations).toArray)

      annotationsOnMethod.exists(_.nonEmpty) &&
      annotationsOnLambda.zipAll(annotationsOnMethod.iterator, null, null).forall {
        case (null, _) => false
        case (_, null) => false
        case (left, right) =>
          left.iterator.zipAll(right.iterator, null, null).forall {
            case (l, r) => (l eq null) || l == r
          }
      }
    }

    // if method reference has more annotations, get parameters from reference instead
    // to preserve annotations!
    if (annotationsOnMethodAreNonEmptyAndASuperset) {
      // Use types from the generated lambda, not the method reference, because method reference types maybe generic/unresolved/unrelated
      // But lambda params should be sufficiently 'grounded' at this point
      // (Besides, lambda types are the ones specified by the caller, we should respect them)
      methodReferenceParams.zip(lambdaParams).map {
        case (mArg, lArg) =>
          mArg.copy(
            symbol = lArg.symbol.withAnnotations(mArg.symbol.annotations),
            key = mArg.key.withTpe(lArg.key.tpe),
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

  protected[this] def analyzeValRef(sig: Type): List[Association.Parameter] = {
    widenFunctionObject(sig).typeArgs.init.map {
      tpe =>
        val symbol = SymbolInfo.Static.syntheticFromType(c.freshName)(tpe)
        reflectionProvider.parameterToAssociation(symbol)
    }
  }

  protected[this] def widenFunctionObject(sig: Type): Type = {
    sig match {
      case s: SingleTypeApi =>
        sig.baseType(s.sym.typeSignature.baseClasses.find(definitions.FunctionClass.seq.contains(_)).get)
      case _ =>
        sig
    }
  }

}
