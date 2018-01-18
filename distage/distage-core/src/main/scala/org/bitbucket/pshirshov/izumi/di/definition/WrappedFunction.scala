package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.model.{Callable, EqualitySafeType}
import org.bitbucket.pshirshov.izumi.di.{Tag, TypeFull}




sealed trait WrappedFunction[+R] extends Callable {
  def ret: TypeFull
  def argTypes: Seq[TypeFull]
  protected def fun: Any
  override def toString: String = {
    s"$fun(${argTypes.mkString(", ")}): $ret"
  }
}

object WrappedFunction {

  implicit class W0[R: Tag, T1: Tag](override protected val fun: () => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq.empty

    override protected def call(args: Any*): Any = fun()
  }

  implicit class W1[R: Tag, T1: Tag](override protected val fun: (T1) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
    )

    override protected def call(args: Any*): Any = fun(
      args.head.asInstanceOf[T1]
    )
  }

  implicit class W2[R: Tag, T1: Tag, T2: Tag](override protected val fun: (T1, T2) => R) extends WrappedFunction[R] {
    def ret: TypeFull = EqualitySafeType.get[R]

    def argTypes: Seq[TypeFull] = Seq(
      EqualitySafeType.get[T1]
      , EqualitySafeType.get[T2]
    )

    override protected def call(args: Any*): Any = fun(
      args.head.asInstanceOf[T1]
      , args.head.asInstanceOf[T2]
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
      args.head.asInstanceOf[T1]
      , args.head.asInstanceOf[T2]
      , args.head.asInstanceOf[T3]
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
      args.head.asInstanceOf[T1]
      , args.head.asInstanceOf[T2]
      , args.head.asInstanceOf[T3]
      , args.head.asInstanceOf[T4]
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
      args.head.asInstanceOf[T1]
      , args.head.asInstanceOf[T2]
      , args.head.asInstanceOf[T3]
      , args.head.asInstanceOf[T4]
      , args.head.asInstanceOf[T5]
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
      args.head.asInstanceOf[T1]
      , args.head.asInstanceOf[T2]
      , args.head.asInstanceOf[T3]
      , args.head.asInstanceOf[T4]
      , args.head.asInstanceOf[T5]
      , args.head.asInstanceOf[T6]
    )
  }

}