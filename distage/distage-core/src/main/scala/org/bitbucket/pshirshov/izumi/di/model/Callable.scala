package org.bitbucket.pshirshov.izumi.di.model

import org.bitbucket.pshirshov.izumi.di.TypeFull
import org.bitbucket.pshirshov.izumi.di.model.exceptions.UnsafeCallArgsMismatched
import scala.reflect.runtime.universe._
trait Callable {
  def argTypes: Seq[TypeFull]

  final def apply(args: Any*): Any = {
    if (verifyArgs(args)) {
      throw new UnsafeCallArgsMismatched(s"Mismatched arguments for unsafe call: $argTypes, $args", argTypes, args)
    }

    call(args: _*)
  }

  private def verifyArgs(args: Any*) = {
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
