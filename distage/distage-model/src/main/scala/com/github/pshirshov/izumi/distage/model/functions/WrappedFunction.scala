package com.github.pshirshov.izumi.distage.model.functions

import com.github.pshirshov.izumi.fundamentals.reflection.{EqualitySafeType, RuntimeUniverse}
import RuntimeUniverse.Callable

sealed trait WrappedFunction[+R] extends Callable {
  def ret: RuntimeUniverse.TypeFull
  def argTypes: Seq[RuntimeUniverse.TypeFull]
  protected def fun: Any
  override def toString: String = {
    s"$fun(${argTypes.mkString(", ")}): $ret"
  }
}

object WrappedFunction {
  import RuntimeUniverse._
  
  def wrap[R]: WrappedFunction[R] => WrappedFunction[R] =
    identity

  implicit class W0[R: Tag](override protected val fun: () => R) extends WrappedFunction[R] {
    def ret: RuntimeUniverse.TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq.empty

    override protected def call(args: Any*): Any = fun()
  }

  implicit class W1[R: Tag, T1: Tag](override protected val fun: (T1) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
    )
  }

  implicit class W2[R: Tag, T1: Tag, T2: Tag](override protected val fun: (T1, T2) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
    )
  }

  implicit class W3[R: Tag, T1: Tag, T2: Tag, T3: Tag](override protected val fun: (T1, T2, T3) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
    )
  }

  implicit class W4[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag](override protected val fun: (T1, T2, T3, T4) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
    )
  }

  implicit class W5[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag](override protected val fun: (T1, T2, T3, T4, T5) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
    )

    override protected def call(args: Any*): Any = fun(
      args(0).asInstanceOf[T1]
      , args(1).asInstanceOf[T2]
      , args(2).asInstanceOf[T3]
      , args(3).asInstanceOf[T4]
      , args(4).asInstanceOf[T5]
    )
  }

  implicit class W6[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
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

  implicit class W7[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
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

  implicit class W8[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
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

  implicit class W9[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
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

  implicit class W10[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
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

  implicit class W11[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
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

  implicit class W12[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag, T12: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
      , EqualitySafeType.get[T12]
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

  implicit class W13[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag, T12: Tag, T13: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
      , EqualitySafeType.get[T12]
      , EqualitySafeType.get[T13]
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

  implicit class W14[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag, T12: Tag, T13: Tag, T14: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
      , EqualitySafeType.get[T12]
      , EqualitySafeType.get[T13]
      , EqualitySafeType.get[T14]
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

  implicit class W15[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag, T12: Tag, T13: Tag, T14: Tag, T15: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
      , EqualitySafeType.get[T12]
      , EqualitySafeType.get[T13]
      , EqualitySafeType.get[T14]
      , EqualitySafeType.get[T15]
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
  implicit class W16[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag, T12: Tag, T13: Tag, T14: Tag, T15: Tag, T16: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
      , EqualitySafeType.get[T12]
      , EqualitySafeType.get[T13]
      , EqualitySafeType.get[T14]
      , EqualitySafeType.get[T15]
      , EqualitySafeType.get[T16]
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

  implicit class W17[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag, T12: Tag, T13: Tag, T14: Tag, T15: Tag, T16: Tag, T17: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
      , EqualitySafeType.get[T12]
      , EqualitySafeType.get[T13]
      , EqualitySafeType.get[T14]
      , EqualitySafeType.get[T15]
      , EqualitySafeType.get[T16]
      , EqualitySafeType.get[T17]
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

  implicit class W18[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag, T12: Tag, T13: Tag, T14: Tag, T15: Tag, T16: Tag, T17: Tag, T18: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
      , EqualitySafeType.get[T12]
      , EqualitySafeType.get[T13]
      , EqualitySafeType.get[T14]
      , EqualitySafeType.get[T15]
      , EqualitySafeType.get[T16]
      , EqualitySafeType.get[T17]
      , EqualitySafeType.get[T18]
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

  implicit class W19[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag, T12: Tag, T13: Tag, T14: Tag, T15: Tag, T16: Tag, T17: Tag, T18: Tag, T19: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
      , EqualitySafeType.get[T12]
      , EqualitySafeType.get[T13]
      , EqualitySafeType.get[T14]
      , EqualitySafeType.get[T15]
      , EqualitySafeType.get[T16]
      , EqualitySafeType.get[T17]
      , EqualitySafeType.get[T18]
      , EqualitySafeType.get[T19]
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

  implicit class W20[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag, T12: Tag, T13: Tag, T14: Tag, T15: Tag, T16: Tag, T17: Tag, T18: Tag, T19: Tag, T20: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
      , EqualitySafeType.get[T12]
      , EqualitySafeType.get[T13]
      , EqualitySafeType.get[T14]
      , EqualitySafeType.get[T15]
      , EqualitySafeType.get[T16]
      , EqualitySafeType.get[T17]
      , EqualitySafeType.get[T18]
      , EqualitySafeType.get[T19]
      , EqualitySafeType.get[T20]
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

  implicit class W21[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag, T12: Tag, T13: Tag, T14: Tag, T15: Tag, T16: Tag, T17: Tag, T18: Tag, T19: Tag, T20: Tag, T21: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
      , EqualitySafeType.get[T12]
      , EqualitySafeType.get[T13]
      , EqualitySafeType.get[T14]
      , EqualitySafeType.get[T15]
      , EqualitySafeType.get[T16]
      , EqualitySafeType.get[T17]
      , EqualitySafeType.get[T18]
      , EqualitySafeType.get[T19]
      , EqualitySafeType.get[T20]
      , EqualitySafeType.get[T21]
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

  implicit class W22[R: Tag, T1: Tag, T2: Tag, T3: Tag, T4: Tag, T5: Tag, T6: Tag, T7: Tag, T8: Tag, T9: Tag, T10: Tag, T11: Tag, T12: Tag, T13: Tag, T14: Tag, T15: Tag, T16: Tag, T17: Tag, T18: Tag, T19: Tag, T20: Tag, T21: Tag, T22: Tag](override protected val fun: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
      , EqualitySafeType.get[T3]
      , EqualitySafeType.get[T4]
      , EqualitySafeType.get[T5]
      , EqualitySafeType.get[T6]
      , EqualitySafeType.get[T7]
      , EqualitySafeType.get[T8]
      , EqualitySafeType.get[T9]
      , EqualitySafeType.get[T10]
      , EqualitySafeType.get[T11]
      , EqualitySafeType.get[T12]
      , EqualitySafeType.get[T13]
      , EqualitySafeType.get[T14]
      , EqualitySafeType.get[T15]
      , EqualitySafeType.get[T16]
      , EqualitySafeType.get[T17]
      , EqualitySafeType.get[T18]
      , EqualitySafeType.get[T19]
      , EqualitySafeType.get[T20]
      , EqualitySafeType.get[T21]
      , EqualitySafeType.get[T22]
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
