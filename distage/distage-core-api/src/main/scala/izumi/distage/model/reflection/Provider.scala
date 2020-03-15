package izumi.distage.model.reflection

import izumi.distage.model.exceptions.UnsafeCallArgsMismatched

trait DIFunction {
  def parameters: Seq[Association.Parameter]
  def argTypes: Seq[SafeType]
  def diKeys: Seq[DIKey]

  def ret: SafeType

  def originalFun: AnyRef
  def fun: Seq[Any] => Any
  def arity: Int

  def isGenerated: Boolean

  def unsafeApply(refs: Seq[TypedRef[_]]): Any = {
    val args = verifyArgs(refs)
    fun(args)
  }

  private[this] def verifyArgs(refs: Seq[TypedRef[_]]): Seq[Any] = {
    val (newArgs, types, typesCmp) = parameters.zip(refs).map {
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
        message =
          s"""Mismatched arguments for unsafe call:
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

trait Provider extends DIFunction {
  def unsafeMap(newRet: SafeType, f: Any => _): Provider
  def unsafeZip(newRet: SafeType, that: Provider): Provider
  def addUnused(keys: Seq[DIKey]): Provider

  override final def diKeys: Seq[DIKey] = parameters.map(_.key)
  override final def argTypes: Seq[SafeType] = parameters.map(_.key.tpe)
  override final val arity: Int = parameters.size

  private def eqField: AnyRef = if (isGenerated) ret else originalFun
  override final def equals(obj: Any): Boolean = {
    obj match {
      case that: Provider =>
        eqField == that.eqField
      case _ =>
        false
    }
  }
  override final def hashCode(): Int = eqField.hashCode()
  override final def toString: String = s"$fun(${argTypes.mkString(", ")}): $ret"
}

object Provider {

  final case class ProviderImpl[+A](
                                     parameters: Seq[Association.Parameter],
                                     ret: SafeType,
                                     originalFun: AnyRef,
                                     fun: Seq[Any] => Any,
                                     isGenerated: Boolean,
                                   ) extends Provider {
    def this(parameters: Seq[Association.Parameter], ret: SafeType, fun: Seq[Any] => Any, isGenerated: Boolean) =
      this(parameters, ret, fun, fun, isGenerated)

    override def unsafeApply(refs: Seq[TypedRef[_]]): A =
      super.unsafeApply(refs).asInstanceOf[A]

    override def unsafeMap(newRet: SafeType, f: Any => _): ProviderImpl[_] =
      copy(ret = newRet, fun = xs => f.apply(fun(xs)))

    override def addUnused(keys: Seq[DIKey]): Provider =
      copy(parameters = parameters ++ keys.map(key => Association.Parameter(SymbolInfo("<unused>", key.tpe, isByName = false, wasGeneric = false), key)))

    override def unsafeZip(newRet: SafeType, that: Provider): Provider = {
      ProviderImpl(
        parameters ++ that.parameters,
        newRet,
        { args0 =>
          val (args1, args2) = args0.splitAt(arity)
          fun(args1) -> that.fun(args2)
        },
        isGenerated,
      )
    }
  }
  object ProviderImpl {
    @inline def apply[A](parameters: Seq[Association.Parameter], ret: SafeType, fun: Seq[Any] => Any, isGenerated: Boolean): ProviderImpl[A] =
      new ProviderImpl(parameters, ret, fun, isGenerated)
  }

}
