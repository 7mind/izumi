package logstage

import com.github.pshirshov.izumi.logstage.api
import com.github.pshirshov.izumi.logstage.api.LoggingMacro.logMessageMacro

import scala.language.experimental.macros

final class LogMessageMacroCtor(private val log: api.Log.Message.type) extends AnyVal {
  /** Construct [[api.Log.Message]] from a string interpolation via a macro */
  def apply(message: String): api.Log.Message = macro logMessageMacro
}
