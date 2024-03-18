package izumi.logstage.api

import izumi.logstage.api.Log.Message
import izumi.logstage.macros.LogMessageMacro

import scala.language.experimental.macros
import scala.language.implicitConversions

trait MessageMat {
  /** Construct [[Message]] from a string interpolation */
  implicit def apply(message: String): Message = macro LogMessageMacro.logMessageMacro
}
