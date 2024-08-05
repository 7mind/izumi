package izumi.fundamentals.platform.exceptions

import izumi.fundamentals.platform.IzPlatformSyntax
import izumi.fundamentals.platform.exceptions.impl.{IzThrowableStackTop_Syntax, IzThrowable_Syntax}

import scala.language.implicitConversions

trait IzThrowable extends IzPlatformSyntax {
  implicit def toRichThrowable(throwable: Throwable): IzThrowable_Syntax = new IzThrowable_Syntax(throwable)
  implicit def toRichThrowableStackTop(throwable: Throwable): IzThrowableStackTop_Syntax = new IzThrowableStackTop_Syntax(throwable, Set.empty)
}

object IzThrowable extends IzThrowable {}
