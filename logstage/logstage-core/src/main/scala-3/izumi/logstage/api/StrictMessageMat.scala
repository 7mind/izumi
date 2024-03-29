package izumi.logstage.api

import izumi.logstage.api.Log.Message
import scala.language.implicitConversions

trait StrictMessageMat {
  /** Construct [[Message]] from a string interpolation */
  transparent inline implicit def apply(inline message: String): Message = ${ LogMessageMacro.message('message, true) }
}
