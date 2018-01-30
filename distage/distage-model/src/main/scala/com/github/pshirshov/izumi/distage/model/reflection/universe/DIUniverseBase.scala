package com.github.pshirshov.izumi.distage.model.reflection.universe

import scala.reflect.api.Universe

trait DIUniverseBase { self =>

  val u: Universe

  case class SelectedConstructor(constructorSymbol: MethodSymb, arguments: List[Symb])

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

    def getWeak[T: u.WeakTypeTag]: TypeFull = SafeType(u.weakTypeTag[T].tpe)

    implicit final val liftableSafeType: u.Liftable[SafeType] =
      value => {
        import u._
        q"{ ${symbolOf[RuntimeUniverse.type].asClass.module}.SafeType.getWeak[${Liftable.liftType(value.tpe)}] }"
      }
  }


  class UnsafeCallArgsMismatched(message: String, val expected: Seq[TypeFull], val actual: Seq[Any]) extends RuntimeException(message, null)

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

  type TypeFull = SafeType
  type Tag[T] = u.TypeTag[T]
  type TypeNative = u.Type
  type Symb = u.Symbol
  type MethodSymb = u.MethodSymbol

}
