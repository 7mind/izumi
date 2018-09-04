package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.{WithDIAssociation, WithDIWiring}
import com.github.pshirshov.izumi.distage.model.references.{WithDIKey, WithDITypedRef}
import com.github.pshirshov.izumi.fundamentals.reflection.WithTags

trait WithDICallable {
  this: DIUniverseBase
    with WithDISafeType
    with WithTags
    with WithDITypedRef
    with WithDIKey
    with WithDISymbolInfo
    with WithDIAssociation
    with WithDIWiring =>

  trait Callable {
    def argTypes: Seq[SafeType]
    def ret: SafeType

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
    def fun: Seq[Any] => Any

    override val argTypes: Seq[SafeType] = associations.map(_.wireWith.tpe)

    override protected def call(args: Any*): Any =
      fun.apply(args: Seq[Any])

    override def toString: String =
      s"$fun(${argTypes.mkString(", ")}): $ret"
  }

  object Provider {

    case class ProviderImpl[+R](associations: Seq[Association.Parameter], ret: SafeType, fun: Seq[Any] => Any) extends Provider {
      override protected def call(args: Any*): R =
        super.call(args: _*).asInstanceOf[R]

      override def unsafeApply(refs: TypedRef[_]*): R =
        super.unsafeApply(refs: _*).asInstanceOf[R]
    }

    object ProviderImpl {
      def apply[R: Tag](associations: Seq[Association.Parameter], fun: Seq[Any] => Any): ProviderImpl[R] =
        new ProviderImpl[R](associations, SafeType.get[R], fun)
    }

    trait FactoryProvider extends Provider {
      def factoryIndex: Map[Int, Wiring.FactoryFunction.WithContext]
    }

    object FactoryProvider {
      case class FactoryProviderImpl(provider: Provider, factoryIndex: Map[Int, Wiring.FactoryFunction.WithContext]) extends FactoryProvider {
        override def associations: Seq[Association.Parameter] = provider.associations
        override def ret: SafeType = provider.ret
        override def fun: Seq[Any] => Any = provider.fun
      }
    }

  }

  class UnsafeCallArgsMismatched(message: String, val expected: Seq[SafeType], val actual: Seq[SafeType], val actualValues: Seq[Any]) extends DIException(message, null)

}
