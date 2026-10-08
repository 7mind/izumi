package izumi.distage.testkit.runner.spec

import scala.compiletime.testing.{ErrorKind, typeCheckErrors}
import scala.quoted.*

trait FrontendAssertions { self: TestAssertions =>
  transparent inline def assertCompiles(inline code: String): Assertion = ${ CompilationAssertionMacro.compiles('code, '{ self }) }

  transparent inline def assertDoesNotCompile(inline code: String): Assertion = ${ CompilationAssertionMacro.doesNotCompile('code, '{ self }) }

  transparent inline def assertTypeError(inline code: String): Assertion = ${ CompilationAssertionMacro.typeError('code, '{ self }) }
}

object CompilationAssertionMacro {
  def compiles(code: Expr[String], receiver: Expr[TestAssertions])(using Quotes): Expr[Unit] = {
    val text = Expr(literal(code))
    '{
      val errors = typeCheckErrors($text)
      if (errors.nonEmpty) $receiver.fail("Expected compilation to succeed:\n" + errors.map(_.message).mkString("\n"))
    }
  }

  def doesNotCompile(code: Expr[String], receiver: Expr[TestAssertions])(using Quotes): Expr[Unit] = {
    val text = Expr(literal(code))
    '{
      val errors = typeCheckErrors($text)
      if (errors.isEmpty) $receiver.fail("Expected compilation to fail:\n" + $text)
    }
  }

  def typeError(code: Expr[String], receiver: Expr[TestAssertions])(using Quotes): Expr[Unit] = {
    val text = Expr(literal(code))
    '{
      val errors = typeCheckErrors($text)
      if (errors.isEmpty) $receiver.fail("Expected a type error, but compilation succeeded:\n" + $text)
      else if (errors.exists(_.kind == ErrorKind.Parser)) $receiver.fail("Expected a type error, but parsing failed:\n" + errors.map(_.message).mkString("\n"))
    }
  }

  private def literal(code: Expr[String])(using Quotes): String = code.value match {
    case Some(value) => value
    case None => code match {
      case '{ scala.Predef.augmentString($text).stripMargin } => text.valueOrAbort.stripMargin
      case _ => quotes.reflect.report.errorAndAbort("Compilation assertions require a literal string", code)
    }
  }
}
