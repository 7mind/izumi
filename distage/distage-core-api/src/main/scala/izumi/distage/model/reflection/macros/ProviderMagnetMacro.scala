package izumi.distage.model.reflection.macros

import izumi.distage.constructors.DebugProperties
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.StaticDIUniverse.Aux
import izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{AnnotationTools, ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

/**
  * To see macro debug output during compilation, set `-Dizumi.debug.macro.distage.providermagnet=true` java property!
  *
  * {{{
  *   sbt -Dizumi.debug.macro.distage.providermagnet=true compile
  * }}}
  *
  * @see [[DebugProperties]]
  */
class ProviderMagnetMacro(c: blackbox.Context) extends ProviderMagnetMacro0(c)

class ProviderMagnetMacro0[C <: blackbox.Context](val c: C) {

  final val macroUniverse: Aux[c.universe.type] = StaticDIUniverse(c)

  private final val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.providermagnet`)
  private final val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)

  import c.universe._
  import macroUniverse._

  case class ExtractedInfo(associations: List[Association.Parameter], isValReference: Boolean)

  def impl[R: c.WeakTypeTag](fun: Tree): c.Expr[ProviderMagnet[R]] = {
    val associations = analyze(fun, weakTypeOf[R])
    val result = generateProvider[R](associations, fun, false)

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
      analyzeValRef(tree.tpe)
    case _ =>
      c.abort(tree.pos,
        s"""
           | Can handle only method references of form (method _) or lambda bodies of form (args => body).\n
           | Argument doesn't seem to be a method reference or a lambda:\n
           |   argument: ${showCode(tree)}\n
           |   argumentTree: ${showRaw(tree)}\n
           | Hint: Try appending _ to your method name""".stripMargin
      )
  }

  def generateProvider[R: c.WeakTypeTag](parameters: List[Association.Parameter],
                                         fun: Tree,
                                         isGenerated: Boolean,
                                        ): c.Expr[ProviderMagnet[R]] = {
    val tools = DIUniverseLiftables(macroUniverse)
    import tools.{liftableParameter, liftTypeToSafeType}

    val (substitutedByNames, casts) = parameters.zipWithIndex.map {
      case (param, i) =>

        val strippedByNameTpe = param.copy(symbol = param.symbol.withTpe {
          ReflectionUtil.stripByName(u)(param.symbol.finalResultType)
        })
        strippedByNameTpe -> q"seqAny($i)"
    }.unzip

    c.Expr[ProviderMagnet[R]] {
      q"""{
        val fun = $fun

        new ${weakTypeOf[ProviderMagnet[R]]}(
          new ${weakTypeOf[RuntimeDIUniverse.Provider.ProviderImpl[R]]}(
            ${Liftable.liftList[Association.Parameter].apply(substitutedByNames)},
            ${liftTypeToSafeType(weakTypeOf[R])},
            fun,
            { seqAny => fun.asInstanceOf[(..${casts.map(_ => definitions.AnyTpe)}) => ${definitions.AnyTpe}](..$casts) },
            $isGenerated,
          )
        )
      }"""
    }
  }

  protected[this] def analyzeMethodRef(lambdaArgs: List[Symbol], body: Tree): List[Association.Parameter] = {
    def association(p: Symbol): Association.Parameter = {
      reflectionProvider.associationFromParameter(SymbolInfo.Runtime(p))
    }

    val lambdaParams = lambdaArgs.map(association)
    val methodReferenceParams = body match {
      case Apply(f, _) =>
        logger.log(s"Matched function body as a method reference - consists of a single call to a function $f - ${showRaw(body)}")

        val params = f.symbol.asMethod.typeSignature.paramLists.flatten
        params.map(association)
      case _ =>
        logger.log(s"Function body didn't match as a variable or a method reference - ${showRaw(body)}")

        List()
    }

    logger.log(s"lambda params: $lambdaParams")
    logger.log(s"method ref params: $methodReferenceParams")

    val annotationsOnLambda = lambdaParams.flatMap(_.symbol.annotations)
    val annotationsOnMethod = methodReferenceParams.flatMap(_.symbol.annotations)

    // if method reference has more annotations, get parameters from reference instead
    // to preserve annotations!
    if (methodReferenceParams.size == lambdaParams.size &&
        annotationsOnLambda.isEmpty && annotationsOnMethod.nonEmpty) {
      // Use types from the generated lambda, not the method reference, because method reference types maybe generic/unresolved
      // But lambda params should be sufficiently 'grounded' at this point
      // (Besides, lambda types are the ones specified by the caller, we should respect them)
      methodReferenceParams.zip(lambdaParams).map {
        case (mArg, lArg) =>
          mArg.copy(
            symbol = mArg.symbol.withTpe(lArg.tpe),
            key = mArg.key.withTpe(lArg.key.tpe),
          )
      }
    } else {
      lambdaParams
    }
  }

  protected[this] def analyzeValRef(sig: Type): List[Association.Parameter] = {
    widenFunctionObject(sig).typeArgs.init.map {
      tpe =>
        val symbol = SymbolInfo.Static(
          name = c.freshName(tpe.typeSymbol.name.toString),
          finalResultType = tpe,
          annotations = AnnotationTools.getAllTypeAnnotations(u)(tpe),
          isByName = tpe.typeSymbol.isClass && tpe.typeSymbol.asClass == definitions.ByNameParamClass,
          wasGeneric = tpe.typeSymbol.isParameter,
        )

        reflectionProvider.associationFromParameter(symbol)
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
