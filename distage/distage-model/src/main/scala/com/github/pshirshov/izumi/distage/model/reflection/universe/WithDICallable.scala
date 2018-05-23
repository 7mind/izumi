package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.distage.model.references.{WithDIKey, WithDITypedRef}

trait WithDICallable {
  this: DIUniverseBase
    with WithDISafeType
    with WithDITypedRef
    with WithDIKey =>

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
    def diKeys: Seq[DIKey]

    override final val argTypes: Seq[TypeFull] = diKeys.map(_.tpe).to
  }

  class UnsafeCallArgsMismatched(message: String, val expected: Seq[TypeFull], val actual: Seq[TypeFull], val actualValues: Seq[Any])
    extends RuntimeException(message, null)

}
