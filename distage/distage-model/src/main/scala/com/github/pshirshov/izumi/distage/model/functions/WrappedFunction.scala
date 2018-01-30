package com.github.pshirshov.izumi.distage.model.functions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

import scala.language.experimental.macros

sealed trait WrappedFunction[+R] extends RuntimeUniverse.Callable {
  def ret: RuntimeUniverse.TypeFull
  def argTypes: Seq[RuntimeUniverse.TypeFull]
  protected def fun: Any
  override def toString: String = {
    s"$fun(${argTypes.mkString(", ")}): $ret"
  }
  def apply(args: Any*): R = unsafeApply(args: _*).asInstanceOf[R]
}

object DIKeyWrappedFunctionMacroImpl {
  import WrappedFunction._

  import scala.reflect.macros._

  def impl[R](c: blackbox.Context)(funcExpr: c.Expr[Any]): c.Expr[DIKeyWrappedFunction[R]] = {
    import c.universe._
    System.err.println(c.universe.showCode(funcExpr.tree))
    System.err.println(c.universe.showRaw(funcExpr.tree))

    c.Expr[DIKeyWrappedFunction[R]]{q"???"}
  }
}

object WrappedFunction {
  import RuntimeUniverse._

  class DIKeyWrappedFunction[+R](val argsWithKeys: Seq[(DIKey, TypeFull)]
                               , val ret: TypeFull
                               , val fun: Seq[Any] => R
  ) extends WrappedFunction[R] {
    val argTypes: Seq[TypeFull] = argsWithKeys.unzip._2

    override protected def call(args: Any*): R = {
      val seq: Seq[Any] = args
      fun.apply(seq)
    }
  }

  object DIKeyWrappedFunction {
    def apply[R](funcExpr: Any): DIKeyWrappedFunction[R] = macro DIKeyWrappedFunctionMacroImpl.impl[R]
  }

  def wrap[T, R](fn: T)(implicit conv: T => WrappedFunction[R]): WrappedFunction[R] =
    conv(fn)

  implicit class W0[R: u.WeakTypeTag](override protected val fun: () => R) extends WrappedFunction[R] {
    def ret: RuntimeUniverse.TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq.empty

    override protected def call(args: Any*): Any = fun()
  }

  implicit class W1[R: u.WeakTypeTag, T1: u.WeakTypeTag](override protected val fun: (T1) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
    )
  }

  implicit class W2[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag](override protected val fun: (T1, T2) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
    )
  }

  implicit class W3[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag](override protected val fun: (T1, T2, T3) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
    )
  }

  implicit class W4[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
    )
  }

  implicit class W5[R: u.WeakTypeTag, T1: u.WeakTypeTag, T2: u.WeakTypeTag, T3: u.WeakTypeTag, T4: u.WeakTypeTag, T5: u.WeakTypeTag](override protected val fun: (T1, T2, T3, T4, T5) => R) extends WrappedFunction[R] {
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
      , RuntimeUniverse.SafeType.getWeak[T12]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
      , RuntimeUniverse.SafeType.getWeak[T12]
      , RuntimeUniverse.SafeType.getWeak[T13]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
      , RuntimeUniverse.SafeType.getWeak[T12]
      , RuntimeUniverse.SafeType.getWeak[T13]
      , RuntimeUniverse.SafeType.getWeak[T14]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
      , RuntimeUniverse.SafeType.getWeak[T12]
      , RuntimeUniverse.SafeType.getWeak[T13]
      , RuntimeUniverse.SafeType.getWeak[T14]
      , RuntimeUniverse.SafeType.getWeak[T15]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
      , RuntimeUniverse.SafeType.getWeak[T12]
      , RuntimeUniverse.SafeType.getWeak[T13]
      , RuntimeUniverse.SafeType.getWeak[T14]
      , RuntimeUniverse.SafeType.getWeak[T15]
      , RuntimeUniverse.SafeType.getWeak[T16]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
      , RuntimeUniverse.SafeType.getWeak[T12]
      , RuntimeUniverse.SafeType.getWeak[T13]
      , RuntimeUniverse.SafeType.getWeak[T14]
      , RuntimeUniverse.SafeType.getWeak[T15]
      , RuntimeUniverse.SafeType.getWeak[T16]
      , RuntimeUniverse.SafeType.getWeak[T17]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
      , RuntimeUniverse.SafeType.getWeak[T12]
      , RuntimeUniverse.SafeType.getWeak[T13]
      , RuntimeUniverse.SafeType.getWeak[T14]
      , RuntimeUniverse.SafeType.getWeak[T15]
      , RuntimeUniverse.SafeType.getWeak[T16]
      , RuntimeUniverse.SafeType.getWeak[T17]
      , RuntimeUniverse.SafeType.getWeak[T18]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
      , RuntimeUniverse.SafeType.getWeak[T12]
      , RuntimeUniverse.SafeType.getWeak[T13]
      , RuntimeUniverse.SafeType.getWeak[T14]
      , RuntimeUniverse.SafeType.getWeak[T15]
      , RuntimeUniverse.SafeType.getWeak[T16]
      , RuntimeUniverse.SafeType.getWeak[T17]
      , RuntimeUniverse.SafeType.getWeak[T18]
      , RuntimeUniverse.SafeType.getWeak[T19]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
      , RuntimeUniverse.SafeType.getWeak[T12]
      , RuntimeUniverse.SafeType.getWeak[T13]
      , RuntimeUniverse.SafeType.getWeak[T14]
      , RuntimeUniverse.SafeType.getWeak[T15]
      , RuntimeUniverse.SafeType.getWeak[T16]
      , RuntimeUniverse.SafeType.getWeak[T17]
      , RuntimeUniverse.SafeType.getWeak[T18]
      , RuntimeUniverse.SafeType.getWeak[T19]
      , RuntimeUniverse.SafeType.getWeak[T20]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
      , RuntimeUniverse.SafeType.getWeak[T12]
      , RuntimeUniverse.SafeType.getWeak[T13]
      , RuntimeUniverse.SafeType.getWeak[T14]
      , RuntimeUniverse.SafeType.getWeak[T15]
      , RuntimeUniverse.SafeType.getWeak[T16]
      , RuntimeUniverse.SafeType.getWeak[T17]
      , RuntimeUniverse.SafeType.getWeak[T18]
      , RuntimeUniverse.SafeType.getWeak[T19]
      , RuntimeUniverse.SafeType.getWeak[T20]
      , RuntimeUniverse.SafeType.getWeak[T21]
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
    def ret: TypeFull = RuntimeUniverse.SafeType.getWeak[R]

    def argTypes: Seq[TypeFull] = Seq(
      RuntimeUniverse.SafeType.getWeak[T1]
      , RuntimeUniverse.SafeType.getWeak[T2]
      , RuntimeUniverse.SafeType.getWeak[T3]
      , RuntimeUniverse.SafeType.getWeak[T4]
      , RuntimeUniverse.SafeType.getWeak[T5]
      , RuntimeUniverse.SafeType.getWeak[T6]
      , RuntimeUniverse.SafeType.getWeak[T7]
      , RuntimeUniverse.SafeType.getWeak[T8]
      , RuntimeUniverse.SafeType.getWeak[T9]
      , RuntimeUniverse.SafeType.getWeak[T10]
      , RuntimeUniverse.SafeType.getWeak[T11]
      , RuntimeUniverse.SafeType.getWeak[T12]
      , RuntimeUniverse.SafeType.getWeak[T13]
      , RuntimeUniverse.SafeType.getWeak[T14]
      , RuntimeUniverse.SafeType.getWeak[T15]
      , RuntimeUniverse.SafeType.getWeak[T16]
      , RuntimeUniverse.SafeType.getWeak[T17]
      , RuntimeUniverse.SafeType.getWeak[T18]
      , RuntimeUniverse.SafeType.getWeak[T19]
      , RuntimeUniverse.SafeType.getWeak[T20]
      , RuntimeUniverse.SafeType.getWeak[T21]
      , RuntimeUniverse.SafeType.getWeak[T22]
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
