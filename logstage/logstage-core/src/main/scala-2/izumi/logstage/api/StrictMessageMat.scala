package izumi.logstage.api

import izumi.logstage.api.Log.Message
import izumi.logstage.macros.LogMessageMacroStrict

import scala.language.experimental.macros
import scala.language.implicitConversions

trait StrictMessageMat {
  /** Construct [[Message]] from a string interpolation */
  implicit def apply(message: String): Message = macro LogMessageMacroStrict.logMessageMacro
}
