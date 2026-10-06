package izumi.distage.testkit.runner.spec

import scala.language.experimental.macros
import scala.reflect.macros.{ParseException, TypecheckException, blackbox}

trait FrontendAssertions { self: TestAssertions =>
  def assertCompiles(code: String): Assertion = macro CompilationAssertionMacro.compiles
  def assertDoesNotCompile(code: String): Assertion = macro CompilationAssertionMacro.doesNotCompile
}

object CompilationAssertionMacro {
  def compiles(c: blackbox.Context)(code: c.Expr[String]): c.Expr[Unit] = expand(c)(code, expectedSuccess = true)
  def doesNotCompile(c: blackbox.Context)(code: c.Expr[String]): c.Expr[Unit] = expand(c)(code, expectedSuccess = false)

  private def expand(c: blackbox.Context)(code: c.Expr[String], expectedSuccess: Boolean): c.Expr[Unit] = {
    import c.universe.*
    val text = code.tree match {
      case Literal(Constant(value: String)) => value
      case _ => c.abort(code.tree.pos, "Compilation assertions require a literal string")
    }
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
