package izumi.distage.testkit.runner.spec

import izumi.distage.testkit.protocol.SourceLocation

import scala.quoted.*

final case class RegistrationPosition(location: SourceLocation)
object RegistrationPosition {
  implicit inline def materialize: RegistrationPosition = ${ RegistrationPositionMacro.materialize }
}

@scala.annotation.publicInBinary
private[spec] object RegistrationPositionMacro {
  def materialize(using Quotes): Expr[RegistrationPosition] = {
    import quotes.reflect.*
    val position = Position.ofMacroExpansion
    '{ RegistrationPosition(SourceLocation.Known(${ Expr(position.sourceFile.path) }, ${ Expr(position.startLine) }, Some(${ Expr(position.startColumn) }))) }
  }
}
