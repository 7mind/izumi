package izumi.distage.testkit.runner.spec

import scala.language.experimental.macros
import scala.reflect.macros.{ParseException, TypecheckException, blackbox}

trait FrontendAssertions { self: TestAssertions =>
  def assertCompiles(code: String): Assertion = macro CompilationAssertionMacro.compiles
  def assertDoesNotCompile(code: String): Assertion = macro CompilationAssertionMacro.doesNotCompile
  def assertTypeError(code: String): Assertion = macro CompilationAssertionMacro.typeError
}

object CompilationAssertionMacro {
  def compiles(c: blackbox.Context)(code: c.Expr[String]): c.Expr[Unit] = expand(c)(code, expectedSuccess = true)
  def doesNotCompile(c: blackbox.Context)(code: c.Expr[String]): c.Expr[Unit] = expand(c)(code, expectedSuccess = false)

  def typeError(c: blackbox.Context)(code: c.Expr[String]): c.Expr[Unit] = {
    import c.universe.*
    val text = literal(c)(code)
    val error = try {
      val tree = c.parse("{" + text + "}")
      try {
        val _ = c.typecheck(tree)
        Some("Expected a type error, but compilation succeeded:\n" + text)
      } catch { case _: TypecheckException => None }
    } catch { case cause: ParseException => Some("Expected a type error, but parsing failed:\n" + cause.getMessage) }
    c.Expr[Unit](error match {
      case Some(message) => q"${c.prefix.tree}.fail($message)"
      case None => q"()"
    })
  }

  private def literal(c: blackbox.Context)(code: c.Expr[String]): String = {
    import c.universe.*
    code.tree match {
      case Literal(Constant(value: String)) => value
      case _ => c.abort(code.tree.pos, "Compilation assertions require a literal string")
    }
  }

  private def expand(c: blackbox.Context)(code: c.Expr[String], expectedSuccess: Boolean): c.Expr[Unit] = {
    import c.universe.*
    val text = literal(c)(code)
    val error = try { val _ = c.typecheck(c.parse("{" + text + "}")); None } catch {
      case cause: TypecheckException => Some(cause.getMessage)
      case cause: ParseException => Some(cause.getMessage)
    }
    val result = (expectedSuccess, error) match {
      case (true, Some(message)) => q"${c.prefix.tree}.fail(${"Expected compilation to succeed:\n" + message})"
      case (false, None) => q"${c.prefix.tree}.fail(${"Expected compilation to fail:\n" + text})"
      case _ => q"()"
    }
    c.Expr[Unit](result)
  }
}
