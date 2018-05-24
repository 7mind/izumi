package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.WithDIAssociation
import com.github.pshirshov.izumi.distage.model.references.{WithDIKey, WithDITypedRef}

trait WithDICallable {
  this: DIUniverseBase
    with WithDISafeType
    with WithDITypedRef
    with WithDIKey
    with WithDIAssociation =>

  trait Callable {
    def argTypes: Seq[TypeFull]
    def ret: TypeFull

    protected def call(args: Any*): Any

    def unsafeApply(refs: TypedRef[_]*): Any = {
      val args = verifyArgs(refs)

      call(args: _*)
    }

    private def verifyArgs(refs: Seq[TypedRef[_]]): Seq[Any] = {
      val countOk = refs.size == argTypes.size

      val typesOk = argTypes.zip(refs).forall {
        case (tpe, arg) =>
          arg.symbol <:< tpe
      }

      val (args, types) = refs.map { case TypedRef(v, t) => (v, t) }.unzip

      if (countOk && typesOk) {
        args
      } else {
        throw new UnsafeCallArgsMismatched(
          s"""Mismatched arguments for unsafe call:
             | ${if(!typesOk) "Wrong types!" else ""}
             | ${if(!countOk) s"Expected number of arguments ${argTypes.size}, but got ${refs.size}" else ""}
             |Expected types [${argTypes.mkString(",")}], got types [${types.mkString(",")}], values: (${args.mkString(",")})""".stripMargin
          , argTypes
          , types
          , args)
      }

    }
  }

  trait Provider extends Callable {
    def associations: Seq[Association.Parameter]
    def diKeys: Seq[DIKey] = associations.map(_.wireWith)

    override final val argTypes: Seq[TypeFull] = associations.map(_.wireWith.tpe)
  }

  object Provider {

    case class ProviderImpl[+R](associations: Seq[Association.Parameter], ret: TypeFull, fun: Seq[Any] => Any) extends Provider {
      override protected def call(args: Any*): R =
        fun.apply(args: Seq[Any]).asInstanceOf[R]

      override def toString: String =
        s"$fun(${argTypes.mkString(", ")}): $ret"

      override def unsafeApply(refs: TypedRef[_]*): R =
        super.unsafeApply(refs: _*).asInstanceOf[R]
    }

    object ProviderImpl {
      def apply[R: Tag](associations: Seq[Association.Parameter], fun: Seq[Any] => Any): ProviderImpl[R] =
        new ProviderImpl[R](associations, SafeType.get[R], fun)
    }

  }

  class UnsafeCallArgsMismatched(message: String, val expected: Seq[TypeFull], val actual: Seq[TypeFull], val actualValues: Seq[Any])
    extends DIException(message, null)

}
