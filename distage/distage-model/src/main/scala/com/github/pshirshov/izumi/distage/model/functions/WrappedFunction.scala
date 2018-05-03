package com.github.pshirshov.izumi.distage.model.functions

import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.fundamentals.reflection.MacroUtil

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.macros.blackbox

private [functions] sealed trait WrappedFunction[+R] extends RuntimeDIUniverse.Callable {
  protected def fun: Any

  override def toString: String = {
    s"$fun(${argTypes.mkString(", ")}): $ret"
  }

  override def unsafeApply(refs: universe.RuntimeDIUniverse.TypedRef[_]*): R = super.unsafeApply(refs: _*).asInstanceOf[R]
}

object WrappedFunction {
  import RuntimeDIUniverse._

  /**
  * Trigger implicit conversion from function into a WrappedFunction
  * */
  def apply[R](fun: WrappedFunction[R]): WrappedFunction[R] = fun

  /**
  * A function that receives its arguments from DI context, including named instances via @Id annotation.
  *
  * Prefer passing a method reference such as (method _)
  *
  * The following syntaxes are supported by extractor macro:
  *
  * Inline lambda:
  *
  *   Bindings.provider[Unit] {
  *     i: Int @Id("special") => ()
  *   }
  *
  * Method reference:
  *   def constructor(@Id("special") i: Int): Unit = ()
  *
  *   Bindings.provider[Unit](constructor _)
  *
  * Function value with annotated signature:
  *
  *   val constructor: Int @Id("special") => Unit = _ => ()
  *
  *   Bindings.provider[Unit](constructor)
  *
  * The following IS NOT SUPPORTED, annotations are lost when converting method to a function value:
  *
  *   def constructorMethod(@Id("special") i: Int): Unit = ()
  *
  *   val constructor = constructorMethod _
  *
  *   Bindings.provider[Unit](constructor) // Will summon regular Int, not a "special" Int from DI context
  *
  * */
  class DIKeyWrappedFunction[+R](val diKeys: Seq[DIKey]
                               , val ret: TypeFull
                               , val fun: Seq[Any] => R
  ) extends WrappedFunction[R] with Provider {
    override protected def call(args: Any*): R = {
      val seq: Seq[Any] = args
      fun.apply(seq)
    }
  }

  object DIKeyWrappedFunction {

    implicit def apply[R](funcExpr: () => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: _ => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
    implicit def apply[R](funcExpr: (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) => R): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]

    class DIKeyWrappedFunctionMacroImpl(val c: blackbox.Context) {

      // FIXME: use symbolIntrospector

      final val macroUniverse = StaticDIUniverse(c)
      private final val logger = MacroUtil.mkLogger[DIKeyWrappedFunctionMacroImpl](c)
      private final val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
      private final val keyProvider = DependencyKeyProviderDefaultImpl.Static(macroUniverse)(symbolIntrospector)

      import c.universe._
      import macroUniverse._

      case class ExtractedInfo(keys: List[DIKey], isValReference: Boolean)

      val context = DependencyContext.ConstructorParameterContext(SafeType.get[this.type])

      def extractParamKey(paramSymbol: Symbol): DIKey =
        keyProvider.keyFromParameter(context, paramSymbol)

      def extractTypeKey(paramType: TypeNative): DIKey =
        keyProvider.keyFromParameterType(context, SafeType(paramType))

      def impl[R: c.WeakTypeTag](funcExpr: c.Expr[_]): c.Expr[DIKeyWrappedFunction[R]] = {

        val logger = MacroUtil.mkLogger[this.type](c)

        val argTree = funcExpr.tree

        val ExtractedInfo(keys, isValReference) = analyze(argTree)

        val wrappedFunction = symbolOf[WrappedFunction.type].asClass.module
        val result = c.Expr[DIKeyWrappedFunction[R]] {
          q"""{
            val wrapped = $wrappedFunction.apply[${weakTypeOf[R]}]($funcExpr)

            val diKeys: ${typeOf[List[RuntimeDIUniverse.DIKey]]} = $keys

            _root_.scala.Predef.assert(wrapped.argTypes.length == diKeys.length, "Impossible Happened! args list has different length than diKeys list")

            new $wrappedFunction.DIKeyWrappedFunction[${weakTypeOf[R]}](
              diKeys
              , wrapped.ret
              , s => wrapped.unsafeApply(s.zip(wrapped.argTypes).map{ case (v, t) => $RuntimeDIUniverse.TypedRef(v, t) }: _*)
            )
          }"""
        }

        logger.log(
          s"""Macro expansion info:
             | Symbol: ${argTree.symbol}\n
             | IsMethodSymbol: ${Option(argTree.symbol).exists(_.isMethod)}\n
             | Extracted Keys: $keys\n
             | IsValReference: $isValReference\n
             | argument: ${showCode(argTree)}\n
             | argumentTree: ${showRaw(argTree)}\n
             | argumentType: ${argTree.tpe}
             | Result code: ${showCode(result.tree)}""".stripMargin
        )

        result
      }

      def analyze: c.Tree => ExtractedInfo = {
        case Block(List(), tree) =>
          analyze(tree)
        case Function(args, body) =>
          analyzeMethodRef(args.map(_.symbol), body)
        case tree if Option(tree.symbol).exists(_.isMethod) =>
          analyzeValRef(tree.symbol)
        case tree =>
          c.abort(c.enclosingPosition
            , s"""
               | Can handle only method references of form (method _) or lambda bodies of form (body => ???).\n
               | Argument doesn't seem to be a method reference or a lambda:\n
               |   argument: ${showCode(tree)}\n
               |   argumentTree: ${showRaw(tree)}\n
               | Hint: Try appending _ to your method name""".stripMargin)
      }

      def analyzeMethodRef(lambdaArgs: List[Symbol], body: Tree): ExtractedInfo = {
        val lambdaKeys: List[DIKey] =
          lambdaArgs.map(extractParamKey)

        val methodReferenceKeys: List[DIKey] = body match {
          case Apply(f, _) =>
            logger.log(s"Matched function body as a method reference - consists of a single call to a function $f - ${showRaw(body)}")

            val params = f.symbol.asMethod.typeSignature.paramLists.flatten
            params.map(extractParamKey)
          case _ =>
            logger.log(s"Function body didn't match as a variable or a method reference - ${showRaw(body)}")

            List()
        }

        val keys = if (methodReferenceKeys.collect {case i: DIKey.IdKey[_] => i}.isEmpty || methodReferenceKeys.size != lambdaKeys.size)
          lambdaKeys
        else
          methodReferenceKeys

        ExtractedInfo(keys, isValReference = false)
      }

      def analyzeValRef(symbol: Symbol): ExtractedInfo = {
        val sig = symbol.typeSignature.finalResultType

        val pars = sig.typeArgs.init.map(extractTypeKey)

        ExtractedInfo(pars, isValReference = true)
      }
    }

  }

  implicit class W0[R: u.WeakTypeTag](override protected val fun: () => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq.empty

    override protected def call(args: Any*): Any = fun()
  }

  implicit class W1[R: u.WeakTypeTag, T1: u.WeakTypeTag](override protected val fun: T1 => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
    )
  }

  implicit class W2[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag](override protected val fun: (T1, T2) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
    )
  }

  implicit class W3[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag](override protected val fun: (T1, T2, T3) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
    )
  }

  implicit class W4[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
    )
  }

  implicit class W5[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
    )
  }

  implicit class W6[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
    )
  }

  implicit class W7[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
    )
  }

  implicit class W8[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
    )
  }

  implicit class W9[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
    )
  }

  implicit class W10[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
    )
  }

  implicit class W11[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
    )
  }

  implicit class W12[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag, T12: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
      , RuntimeDIUniverse.SafeType.getWeak[T12]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
      , args(11).asInstanceOf[T12]
    )
  }

  implicit class W13[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag, T12: u.WeakTypeTag, T13: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
      , RuntimeDIUniverse.SafeType.getWeak[T12]
      , RuntimeDIUniverse.SafeType.getWeak[T13]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
      , args(11).asInstanceOf[T12]
      , args(12).asInstanceOf[T13]
    )
  }

  implicit class W14[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag, T12: u.WeakTypeTag, T13: u.WeakTypeTag, T14: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
      , RuntimeDIUniverse.SafeType.getWeak[T12]
      , RuntimeDIUniverse.SafeType.getWeak[T13]
      , RuntimeDIUniverse.SafeType.getWeak[T14]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
      , args(11).asInstanceOf[T12]
      , args(12).asInstanceOf[T13]
      , args(13).asInstanceOf[T14]
    )
  }

  implicit class W15[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag, T12: u.WeakTypeTag, T13: u.WeakTypeTag, T14: u.WeakTypeTag, T15: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
      , RuntimeDIUniverse.SafeType.getWeak[T12]
      , RuntimeDIUniverse.SafeType.getWeak[T13]
      , RuntimeDIUniverse.SafeType.getWeak[T14]
      , RuntimeDIUniverse.SafeType.getWeak[T15]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
      , args(11).asInstanceOf[T12]
      , args(12).asInstanceOf[T13]
      , args(13).asInstanceOf[T14]
      , args(14).asInstanceOf[T15]
    )
  }
  implicit class W16[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag, T12: u.WeakTypeTag, T13: u.WeakTypeTag, T14: u.WeakTypeTag, T15: u.WeakTypeTag, T16: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
      , RuntimeDIUniverse.SafeType.getWeak[T12]
      , RuntimeDIUniverse.SafeType.getWeak[T13]
      , RuntimeDIUniverse.SafeType.getWeak[T14]
      , RuntimeDIUniverse.SafeType.getWeak[T15]
      , RuntimeDIUniverse.SafeType.getWeak[T16]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
      , args(11).asInstanceOf[T12]
      , args(12).asInstanceOf[T13]
      , args(13).asInstanceOf[T14]
      , args(14).asInstanceOf[T15]
      , args(15).asInstanceOf[T16]
    )
  }

  implicit class W17[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag, T12: u.WeakTypeTag, T13: u.WeakTypeTag, T14: u.WeakTypeTag, T15: u.WeakTypeTag, T16: u.WeakTypeTag, T17: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
      , RuntimeDIUniverse.SafeType.getWeak[T12]
      , RuntimeDIUniverse.SafeType.getWeak[T13]
      , RuntimeDIUniverse.SafeType.getWeak[T14]
      , RuntimeDIUniverse.SafeType.getWeak[T15]
      , RuntimeDIUniverse.SafeType.getWeak[T16]
      , RuntimeDIUniverse.SafeType.getWeak[T17]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
      , args(11).asInstanceOf[T12]
      , args(12).asInstanceOf[T13]
      , args(13).asInstanceOf[T14]
      , args(14).asInstanceOf[T15]
      , args(15).asInstanceOf[T16]
      , args(16).asInstanceOf[T17]
    )
  }

  implicit class W18[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag, T12: u.WeakTypeTag, T13: u.WeakTypeTag, T14: u.WeakTypeTag, T15: u.WeakTypeTag, T16: u.WeakTypeTag, T17: u.WeakTypeTag, T18: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
      , RuntimeDIUniverse.SafeType.getWeak[T12]
      , RuntimeDIUniverse.SafeType.getWeak[T13]
      , RuntimeDIUniverse.SafeType.getWeak[T14]
      , RuntimeDIUniverse.SafeType.getWeak[T15]
      , RuntimeDIUniverse.SafeType.getWeak[T16]
      , RuntimeDIUniverse.SafeType.getWeak[T17]
      , RuntimeDIUniverse.SafeType.getWeak[T18]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
      , args(11).asInstanceOf[T12]
      , args(12).asInstanceOf[T13]
      , args(13).asInstanceOf[T14]
      , args(14).asInstanceOf[T15]
      , args(15).asInstanceOf[T16]
      , args(16).asInstanceOf[T17]
      , args(17).asInstanceOf[T18]
    )
  }

  implicit class W19[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag, T12: u.WeakTypeTag, T13: u.WeakTypeTag, T14: u.WeakTypeTag, T15: u.WeakTypeTag, T16: u.WeakTypeTag, T17: u.WeakTypeTag, T18: u.WeakTypeTag, T19: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
      , RuntimeDIUniverse.SafeType.getWeak[T12]
      , RuntimeDIUniverse.SafeType.getWeak[T13]
      , RuntimeDIUniverse.SafeType.getWeak[T14]
      , RuntimeDIUniverse.SafeType.getWeak[T15]
      , RuntimeDIUniverse.SafeType.getWeak[T16]
      , RuntimeDIUniverse.SafeType.getWeak[T17]
      , RuntimeDIUniverse.SafeType.getWeak[T18]
      , RuntimeDIUniverse.SafeType.getWeak[T19]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
      , args(11).asInstanceOf[T12]
      , args(12).asInstanceOf[T13]
      , args(13).asInstanceOf[T14]
      , args(14).asInstanceOf[T15]
      , args(15).asInstanceOf[T16]
      , args(16).asInstanceOf[T17]
      , args(17).asInstanceOf[T18]
      , args(18).asInstanceOf[T19]
    )
  }

  implicit class W20[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag, T12: u.WeakTypeTag, T13: u.WeakTypeTag, T14: u.WeakTypeTag, T15: u.WeakTypeTag, T16: u.WeakTypeTag, T17: u.WeakTypeTag, T18: u.WeakTypeTag, T19: u.WeakTypeTag, T20: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
      , RuntimeDIUniverse.SafeType.getWeak[T12]
      , RuntimeDIUniverse.SafeType.getWeak[T13]
      , RuntimeDIUniverse.SafeType.getWeak[T14]
      , RuntimeDIUniverse.SafeType.getWeak[T15]
      , RuntimeDIUniverse.SafeType.getWeak[T16]
      , RuntimeDIUniverse.SafeType.getWeak[T17]
      , RuntimeDIUniverse.SafeType.getWeak[T18]
      , RuntimeDIUniverse.SafeType.getWeak[T19]
      , RuntimeDIUniverse.SafeType.getWeak[T20]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
      , args(11).asInstanceOf[T12]
      , args(12).asInstanceOf[T13]
      , args(13).asInstanceOf[T14]
      , args(14).asInstanceOf[T15]
      , args(15).asInstanceOf[T16]
      , args(16).asInstanceOf[T17]
      , args(17).asInstanceOf[T18]
      , args(18).asInstanceOf[T19]
      , args(19).asInstanceOf[T20]
    )
  }

  implicit class W21[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag, T12: u.WeakTypeTag, T13: u.WeakTypeTag, T14: u.WeakTypeTag, T15: u.WeakTypeTag, T16: u.WeakTypeTag, T17: u.WeakTypeTag, T18: u.WeakTypeTag, T19: u.WeakTypeTag, T20: u.WeakTypeTag, T21: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
      , RuntimeDIUniverse.SafeType.getWeak[T12]
      , RuntimeDIUniverse.SafeType.getWeak[T13]
      , RuntimeDIUniverse.SafeType.getWeak[T14]
      , RuntimeDIUniverse.SafeType.getWeak[T15]
      , RuntimeDIUniverse.SafeType.getWeak[T16]
      , RuntimeDIUniverse.SafeType.getWeak[T17]
      , RuntimeDIUniverse.SafeType.getWeak[T18]
      , RuntimeDIUniverse.SafeType.getWeak[T19]
      , RuntimeDIUniverse.SafeType.getWeak[T20]
      , RuntimeDIUniverse.SafeType.getWeak[T21]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
      , args(11).asInstanceOf[T12]
      , args(12).asInstanceOf[T13]
      , args(13).asInstanceOf[T14]
      , args(14).asInstanceOf[T15]
      , args(15).asInstanceOf[T16]
      , args(16).asInstanceOf[T17]
      , args(17).asInstanceOf[T18]
      , args(18).asInstanceOf[T19]
      , args(19).asInstanceOf[T20]
      , args(20).asInstanceOf[T21]
    )
  }

  implicit class W22[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag, T6: u.WeakTypeTag, T7: u.WeakTypeTag, T8: u.WeakTypeTag, T9: u.WeakTypeTag, T10: u.WeakTypeTag, T11: u.WeakTypeTag, T12: u.WeakTypeTag, T13: u.WeakTypeTag, T14: u.WeakTypeTag, T15: u.WeakTypeTag, T16: u.WeakTypeTag, T17: u.WeakTypeTag, T18: u.WeakTypeTag, T19: u.WeakTypeTag, T20: u.WeakTypeTag, T21: u.WeakTypeTag, T22: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeDIUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeDIUniverse.SafeType.getWeak[T1]
      , RuntimeDIUniverse.SafeType.getWeak[T2]
      , RuntimeDIUniverse.SafeType.getWeak[T3]
      , RuntimeDIUniverse.SafeType.getWeak[T4]
      , RuntimeDIUniverse.SafeType.getWeak[T5]
      , RuntimeDIUniverse.SafeType.getWeak[T6]
      , RuntimeDIUniverse.SafeType.getWeak[T7]
      , RuntimeDIUniverse.SafeType.getWeak[T8]
      , RuntimeDIUniverse.SafeType.getWeak[T9]
      , RuntimeDIUniverse.SafeType.getWeak[T10]
      , RuntimeDIUniverse.SafeType.getWeak[T11]
      , RuntimeDIUniverse.SafeType.getWeak[T12]
      , RuntimeDIUniverse.SafeType.getWeak[T13]
      , RuntimeDIUniverse.SafeType.getWeak[T14]
      , RuntimeDIUniverse.SafeType.getWeak[T15]
      , RuntimeDIUniverse.SafeType.getWeak[T16]
      , RuntimeDIUniverse.SafeType.getWeak[T17]
      , RuntimeDIUniverse.SafeType.getWeak[T18]
      , RuntimeDIUniverse.SafeType.getWeak[T19]
      , RuntimeDIUniverse.SafeType.getWeak[T20]
      , RuntimeDIUniverse.SafeType.getWeak[T21]
      , RuntimeDIUniverse.SafeType.getWeak[T22]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
      , args(5).asInstanceOf[T6]
      , args(6).asInstanceOf[T7]
      , args(7).asInstanceOf[T8]
      , args(8).asInstanceOf[T9]
      , args(9).asInstanceOf[T10]
      , args(10).asInstanceOf[T11]
      , args(11).asInstanceOf[T12]
      , args(12).asInstanceOf[T13]
      , args(13).asInstanceOf[T14]
      , args(14).asInstanceOf[T15]
      , args(15).asInstanceOf[T16]
      , args(16).asInstanceOf[T17]
      , args(17).asInstanceOf[T18]
      , args(18).asInstanceOf[T19]
      , args(19).asInstanceOf[T20]
      , args(20).asInstanceOf[T21]
      , args(21).asInstanceOf[T22]
    )
  }

}
