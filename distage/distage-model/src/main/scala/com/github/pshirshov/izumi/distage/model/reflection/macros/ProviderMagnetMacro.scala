package com.github.pshirshov.izumi.distage.model.reflection.macros

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

import scala.reflect.macros.blackbox

class ProviderMagnetMacroGenerateUnsafeWeakSafeTypes(override val c: blackbox.Context) extends ProviderMagnetMacro(c) {
  override protected def generateUnsafeWeakSafeTypes: Boolean = true
}

/**
* To see macro debug output during compilation, set `-Dizumi.distage.debug.macro=true` java property! i.e.
* {{{
* sbt -Dizumi.distage.debug.macro=true compile
* }}}
*/
// TODO: bench and optimize
class ProviderMagnetMacro(val c: blackbox.Context) {

  protected def generateUnsafeWeakSafeTypes: Boolean = false

  final val macroUniverse = StaticDIUniverse(c)

  private final val logger = TrivialMacroLogger[this.type](c)
  private final val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
  private final val keyProvider = DependencyKeyProviderDefaultImpl.Static(macroUniverse)(symbolIntrospector)
  private final val tools =
    if (generateUnsafeWeakSafeTypes)
      DIUniverseLiftables.generateUnsafeWeakSafeTypes(macroUniverse)
    else
      DIUniverseLiftables(macroUniverse)

  import tools.{liftableParameter, liftableSafeType}
  import c.universe._
  import macroUniverse._

  case class ExtractedInfo(associations: List[Association.Parameter], isValReference: Boolean)

  def impl[R: c.WeakTypeTag](fun: c.Expr[_]): c.Expr[ProviderMagnet[R]] = {
    val argTree = fun.tree
    val ret = SafeType(weakTypeOf[R])

    val ExtractedInfo(associations, isValReference) = analyze(argTree, ret)

    val casts = associations.map(_.tpe.tpe).zipWithIndex.map {
      case (t, i) =>
        q"{ seqAny($i).asInstanceOf[$t] }"
    }

    val result = c.Expr[ProviderMagnet[R]] {
      q"""{
        val fun = $fun

        val associations: ${typeOf[List[RuntimeDIUniverse.Association.Parameter]]} = $associations

        new ${weakTypeOf[ProviderMagnet[R]]}(
          new ${weakTypeOf[RuntimeDIUniverse.Provider.ProviderImpl[R]]}(
            associations
            , $ret
            , {
              seqAny =>
                _root_.scala.Predef.assert(seqAny.size == associations.size, "Impossible Happened! args list has different length than associations list")

                fun(..$casts)
              }
          )
        )
      }"""
    }

    logger.log(
      s"""DIKeyWrappedFunction info:
         | generateUnsafeWeakSafeTypes: $generateUnsafeWeakSafeTypes\n
         | Symbol: ${argTree.symbol}\n
         | IsMethodSymbol: ${Option(argTree.symbol).exists(_.isMethod)}\n
         | Extracted Annotations: ${associations.flatMap(_.context.symbol.annotations)}\n
         | Extracted DIKeys: ${associations.map(_.wireWith)}\n
         | IsValReference: $isValReference\n
         | argument: ${showCode(argTree)}\n
         | argumentTree: ${showRaw(argTree)}\n
         | argumentType: ${argTree.tpe}
         | Result code: ${showCode(result.tree)}""".stripMargin
    )

    result
  }

  def analyze(tree: c.Tree, ret: SafeType): ExtractedInfo = tree match {
    case Block(List(), inner) =>
      analyze(inner, ret)
    case Function(args, body) =>
      analyzeMethodRef(args.map(_.symbol), body, ret)
    case _ if Option(tree.symbol).exists(_.isMethod) =>
      analyzeValRef(tree.symbol, ret)
    case _ =>
      c.abort(c.enclosingPosition
        ,
        s"""
           | Can handle only method references of form (method _) or lambda bodies of form (args => body).\n
           | Argument doesn't seem to be a method reference or a lambda:\n
           |   argument: ${showCode(tree)}\n
           |   argumentTree: ${showRaw(tree)}\n
           | Hint: Try appending _ to your method name""".stripMargin
      )
  }

  private def association(ret: SafeType)(p: Symb): Association.Parameter =
    keyProvider.associationFromParameter(SymbolInfo(p, ret))

  def analyzeMethodRef(lambdaArgs: List[Symbol], body: Tree, ret: SafeType): ExtractedInfo = {
    val lambdaKeys: List[Association.Parameter] =
      lambdaArgs.map(association(ret))

    val methodReferenceKeys: List[Association.Parameter] = body match {
      case Apply(f, _) =>
        logger.log(s"Matched function body as a method reference - consists of a single call to a function $f - ${showRaw(body)}")

        val params = f.symbol.asMethod.typeSignature.paramLists.flatten
        params.map(association(ret))
      case _ =>
        logger.log(s"Function body didn't match as a variable or a method reference - ${showRaw(body)}")

        List()
    }

    logger.log(s"lambda keys: $lambdaKeys")
    logger.log(s"method ref keys: $methodReferenceKeys")

    val annotationsOnLambda: List[u.Annotation] = lambdaKeys.flatMap(_.context.symbol.annotations)
    val annotationsOnMethod: List[u.Annotation] = methodReferenceKeys.flatMap(_.context.symbol.annotations)

    val keys = if (
      methodReferenceKeys.size == lambdaKeys.size &&
        annotationsOnLambda.isEmpty && annotationsOnMethod.nonEmpty) {
      // Use types from the generated lambda, not the method reference, because method reference types maybe generic/unresolved
      //
      // (Besides, lambda types are the ones specified by the caller, we should always use them)
      methodReferenceKeys.zip(lambdaKeys).map {
        case (m, l) =>
          m.copy(tpe = l.tpe, wireWith = m.wireWith.withTpe(l.wireWith.tpe)) // gotcha: symbol not altered
      }
    } else {
      lambdaKeys
    }

    ExtractedInfo(keys, isValReference = false)
  }

  def analyzeValRef(valRef: Symbol, ret: SafeType): ExtractedInfo = {
    val sig = valRef.typeSignature.finalResultType

    val associations = sig.typeArgs.init.map(SafeType(_)).map {
      tpe =>
        val symbol = SymbolInfo.Static(
          c.freshName(tpe.tpe.typeSymbol.name.toString)
          , tpe
          , AnnotationTools.getAllTypeAnnotations(u)(tpe.tpe)
          , ret
        )

        keyProvider.associationFromParameter(symbol)
    }

    ExtractedInfo(associations, isValReference = true)
  }

}
