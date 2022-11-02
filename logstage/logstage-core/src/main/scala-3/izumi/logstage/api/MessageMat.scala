package izumi.logstage.api

import izumi.logstage.api.Log.Message
import scala.language.implicitConversions

trait MessageMat {
  /** Construct [[Message]] from a string interpolation */
  transparent inline implicit def apply(inline message: String): Message = ${ LogMessageMacro.message('message, false) }
  transparent inline def strict(inline message: String): Message = ${ LogMessageMacro.message('message, true) }
}
