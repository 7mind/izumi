package izumi.distage.testkit.runner.spec

import izumi.distage.testkit.protocol.SourceLocation

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final case class RegistrationPosition(location: SourceLocation)
object RegistrationPosition {
  implicit def materialize: RegistrationPosition = macro RegistrationPositionMacro.materialize
}

object RegistrationPositionMacro {
  def materialize(c: blackbox.Context): c.Expr[RegistrationPosition] = {
    import c.universe.*
    val position = c.enclosingPosition
    val location =
      if (position != NoPosition) q"_root_.izumi.distage.testkit.protocol.SourceLocation.Known(${position.source.path}, ${position.line - 1}, _root_.scala.Some(${position.column - 1}))"
      else q"_root_.izumi.distage.testkit.protocol.SourceLocation.Unavailable"
    c.Expr[RegistrationPosition](q"_root_.izumi.distage.testkit.runner.spec.RegistrationPosition($location)")
  }
}
