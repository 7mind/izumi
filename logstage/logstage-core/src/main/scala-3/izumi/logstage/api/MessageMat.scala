package izumi.logstage.api

import izumi.logstage.api.Log.Message
import scala.language.implicitConversions

trait MessageMat {
  /** Construct [[Message]] from a string interpolation */
  inline implicit def apply(inline message: String): Message = ${ LogMessageMacro.message('message) }
}

