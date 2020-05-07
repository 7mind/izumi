package izumi.distage.model.reflection

import izumi.distage.model.exceptions.UnsafeCallArgsMismatched
import izumi.distage.model.reflection.Provider.ProviderType

trait Provider {
  def replaceKeys(f: DIKey => DIKey): Provider = ???

  def parameters: Seq[AssociationP]
  final def diKeys: Seq[DIKey] = parameters.map(_.key)
  final def argTypes: Seq[SafeType] = parameters.map(_.key.tpe)
  final def arity: Int = parameters.size
  def ret: SafeType
  def originalFun: AnyRef
  def fun: Seq[Any] => Any
  def providerType: ProviderType

  def unsafeApply(refs: Seq[TypedRef[_]]): Any = {
    val args = verifyArgs(refs)
    fun(args)
  }

  def unsafeMap(newRet: SafeType, f: Any => _): Provider
  def unsafeZip(newRet: SafeType, that: Provider): Provider
  def addUnused(keys: Seq[DIKey]): Provider

  private val eqField: AnyRef = {
    if (providerType eq ProviderType.Function) originalFun
    else if (providerType eq ProviderType.FunctionWithUnusedKeys) (originalFun, diKeys)
    else ret
  }
  override final def equals(obj: Any): Boolean = obj match {
    case that: Provider => eqField == that.eqField
    case _ => false
  }
  override final def hashCode(): Int = eqField.hashCode()
  override final def toString: String = s"$funString(${argTypes.mkString(", ")}): $ret"
  final def funString = if (providerType eq ProviderType.Function) fun.toString else providerType.toString

  protected[this] def verifyArgs(refs: Seq[TypedRef[_]]): Seq[Any] = {
    val (newArgs, types, typesCmp) = parameters
      .zip(refs).map {
        case (param, TypedRef(v, tpe, isByName)) =>
          val newArg = if (param.isByName && !isByName) {
            () => v
          } else if (isByName && !param.isByName) {
            v.asInstanceOf[Function0[Any]].apply()
          } else v

          (newArg, tpe, tpe <:< param.key.tpe)
      }.unzip3

    val countOk = refs.size == parameters.size
    val typesOk = !typesCmp.contains(false)

    if (countOk && typesOk) {
      newArgs
    } else {
      throw new UnsafeCallArgsMismatched(
        message = s"""Mismatched arguments for unsafe call:
          | ${if (!typesOk) "Wrong types!" else ""}
          | ${if (!countOk) s"Expected number of arguments $arity, but got ${refs.size}" else ""}
          |Expected types [${argTypes.mkString(",")}], got types [${types.mkString(",")}], values: (${newArgs.mkString(",")})""".stripMargin,
        expected = argTypes,
        actual = types,
        actualValues = newArgs,
      )
    }
  }
}

object Provider {

  sealed trait ProviderType
  object ProviderType {
    case object Class extends ProviderType
    case object Trait extends ProviderType
    case object ZIOHas extends ProviderType
    case object Factory extends ProviderType
    case object Singleton extends ProviderType
    case object Function extends ProviderType
    case object FunctionWithUnusedKeys extends ProviderType
  }

  final case class ProviderImpl[+A](
    parameters: Seq[AssociationP],
    ret: SafeType,
    originalFun: AnyRef,
    fun: Seq[Any] => Any,
    providerType: ProviderType,
  ) extends Provider {
    def this(parameters: Seq[AssociationP], ret: SafeType, fun: Seq[Any] => Any, providerType: ProviderType) =
      this(parameters, ret, fun, fun, providerType)

    override def unsafeApply(refs: Seq[TypedRef[_]]): A =
      super.unsafeApply(refs).asInstanceOf[A]

    override def unsafeMap(newRet: SafeType, f: Any => _): ProviderImpl[_] =
      copy(
        ret = newRet,
        originalFun = f,
        fun = xs => f.apply(fun(xs)),
        providerType = ProviderType.Function,
      )

    override def addUnused(keys: Seq[DIKey]): Provider =
      copy(
        parameters = parameters ++ keys.map(key => AssociationP.Parameter(SymbolInfo("<unused>", key.tpe, isByName = false, wasGeneric = false), key)),
        providerType = ProviderType.FunctionWithUnusedKeys,
      )

    override def unsafeZip(newRet: SafeType, that: Provider): Provider = {
      new ProviderImpl(
        parameters = parameters ++ that.parameters,
        ret = newRet,
        fun = {
          args0 =>
            val (args1, args2) = args0.splitAt(arity)
            fun(args1) -> that.fun(args2)
        },
        providerType = ProviderType.Function,
      )
    }
  }
  object ProviderImpl {
    @inline def apply[A](parameters: Seq[AssociationP], ret: SafeType, fun: Seq[Any] => Any, providerType: ProviderType): ProviderImpl[A] =
      new ProviderImpl(parameters, ret, fun, providerType)
  }

}
