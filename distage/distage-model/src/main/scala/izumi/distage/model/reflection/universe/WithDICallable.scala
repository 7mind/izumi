package izumi.distage.model.reflection.universe

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.plan.{WithDIAssociation, WithDIWiring}
import izumi.distage.model.references.{WithDIKey, WithDITypedRef}

trait WithDICallable {
  this: DIUniverseBase with WithDISafeType with WithTags with WithDITypedRef with WithDIKey with WithDISymbolInfo with WithDIAssociation with WithDIWiring =>

  trait Callable {
    def argTypes: Seq[SafeType]
    def ret: SafeType
    def fun: Seq[Any] => Any
    def arity: Int

    def unsafeApply(refs: TypedRef[_]*): Any = {
      val args = verifyArgs(refs)

      fun(args)
    }

    private[this] def verifyArgs(refs: Seq[TypedRef[_]]): Seq[Any] = {
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
          message = s"""Mismatched arguments for unsafe call:
                       | ${if (!typesOk) "Wrong types!" else ""}
                       | ${if (!countOk) s"Expected number of arguments ${argTypes.size}, but got ${refs.size}" else ""}
                       |Expected types [${argTypes.mkString(",")}], got types [${types.mkString(",")}], values: (${args.mkString(",")})""".stripMargin,
          expected = argTypes,
          actual = types,
          actualValues = args,
        )
      }
    }
  }

  trait Provider extends Callable {
    def associations: Seq[Association.Parameter]
    def unsafeMap(newRet: SafeType, f: Any => _): Provider
    def unsafeZip(newRet: SafeType, that: Provider): Provider

    final val diKeys: Seq[DIKey] = associations.map(_.wireWith)
    override final val argTypes: Seq[SafeType] = associations.map(_.wireWith.tpe)
    override final val arity: Int = argTypes.size

    override final def toString: String =
      s"$fun(${argTypes.mkString(", ")}): $ret"
  }

  object Provider {

    case class ProviderImpl[+A](
      associations: Seq[Association.Parameter],
      ret: SafeType,
      fun: Seq[Any] => Any,
    ) extends Provider {

      override final def unsafeApply(refs: TypedRef[_]*): A =
        super.unsafeApply(refs: _*).asInstanceOf[A]

      override final def unsafeMap(newRet: SafeType, f: Any => _): ProviderImpl[_] =
        copy(ret = newRet, fun = xs => f.apply(fun(xs)))

      override final def unsafeZip(newRet: SafeType, that: Provider): Provider =
        ProviderImpl(
          associations ++ that.associations,
          newRet, {
            args0 =>
              val (args1, args2) = args0.splitAt(arity)
              fun(args1) -> that.fun(args2)
          },
        )
    }

    object ProviderImpl {
      def apply[R: Tag](associations: Seq[Association.Parameter], fun: Seq[Any] => Any): ProviderImpl[R] =
        new ProviderImpl[R](associations, SafeType.get[R], fun)
    }

    trait FactoryProvider extends Provider {
      def factoryIndex: Map[Int, Wiring.FactoryFunction.FactoryMethod]
    }

    object FactoryProvider {
      case class FactoryProviderImpl(provider: Provider, factoryIndex: Map[Int, Wiring.FactoryFunction.FactoryMethod]) extends FactoryProvider {
        override final def associations: Seq[Association.Parameter] = provider.associations
        override final def ret: SafeType = provider.ret
        override final def fun: Seq[Any] => Any = provider.fun

        override final def unsafeMap(newRet: SafeType, f: Any => _): FactoryProviderImpl =
          copy(provider = provider.unsafeMap(newRet, f))

        override final def unsafeZip(newRet: SafeType, that: Provider): FactoryProviderImpl = {
          that match {
            case that: FactoryProviderImpl =>
              throw new FactoryProvidersCannotBeCombined(s"Impossible operation: two factory providers cannot be zipped. this=$this that=$that", this, that)
            case _ =>
              copy(provider = provider.unsafeZip(newRet, that))
          }
        }
      }
    }

  }

  class UnsafeCallArgsMismatched(message: String, val expected: Seq[SafeType], val actual: Seq[SafeType], val actualValues: Seq[Any]) extends DIException(message, null)

  class FactoryProvidersCannotBeCombined(message: String, val provider1: Provider.FactoryProvider, val provider2: Provider.FactoryProvider)
    extends DIException(message, null)

}
