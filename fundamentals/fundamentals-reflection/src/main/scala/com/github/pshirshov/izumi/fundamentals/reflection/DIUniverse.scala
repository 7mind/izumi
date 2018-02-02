package com.github.pshirshov.izumi.fundamentals.reflection


import scala.reflect.api.Universe



trait DIUniverse {
  val u: Universe
  val mirror: u.Mirror

  case class SelectedConstructor(constructorSymbol: TypeNative, arguments: Seq[TypeSymb])

  case class SafeType(tpe: TypeNative) {

    override def toString: String = tpe.toString

    override def hashCode(): Int = tpe.toString.hashCode

    override def equals(obj: scala.Any): Boolean = obj match {
      case SafeType(other) =>
        tpe =:= other
      case _ =>
        false
    }
  }

  object SafeType {
    def get[T: Tag]: TypeFull = SafeType(u.typeTag[T].tpe)
  }


  class UnsafeCallArgsMismatched(message: String, val expected: Seq[TypeFull], val actual: Seq[Any]) extends RuntimeException(message, null)

  trait Callable {
    def argTypes: Seq[TypeFull]

    final def apply(args: Any*): Any = {
      if (verifyArgs(args)) {
        throw new UnsafeCallArgsMismatched(s"Mismatched arguments for unsafe call: $argTypes, $args", argTypes, args)
      }

      call(args: _*)
    }

    private def verifyArgs(args: Any*) = {
      // TODO: obsiously this method can only work in runtime
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


  type TypeFull = SafeType
  type Tag[T] = u.TypeTag[T]
  type TypeNative = u.Type
  type TypeSymb = u.Symbol
  type MethodSymb = u.MethodSymbol

}
