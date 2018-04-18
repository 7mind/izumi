package com.github.pshirshov.izumi.distage.model.reflection.universe

trait Callable {
  this: DIUniverseBase
    with SafeType =>

  trait Callable {
    def argTypes: Seq[TypeFull]
    def ret: TypeFull

    final def unsafeApply(args: Any*): Any = {
      if (verifyArgs(args)) {
        throw new UnsafeCallArgsMismatched(s"Mismatched arguments for unsafe call: $argTypes, $args", argTypes, args)
      }

      call(args: _*)
    }

    private def verifyArgs(args: Any*) = {
      // TODO: obviously this method can only work at runtime
      import scala.reflect.runtime.universe._

      val countOk = args.size == argTypes.size

      // TODO:
      val typesOk = argTypes.zip(args).forall {
        case (tpe, value) =>
          val valueBases = runtimeMirror(value.getClass.getClassLoader).reflect(value).symbol.baseClasses
          valueBases.contains(tpe.tpe.typeSymbol)
      }

      countOk && typesOk
    }

    protected def call(args: Any*): Any
  }

  class UnsafeCallArgsMismatched(message: String, val expected: Seq[TypeFull], val actual: Seq[Any]) extends RuntimeException(message, null)

}
