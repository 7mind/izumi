package izumi.idealingua.runtime.rpc.http4s

import izumi.idealingua.runtime.rpc.IRTTransportException
import org.http4s.Status

abstract class IRTHttpFailureException(
                                    message: String
                                    , val status: Status
                                    , cause: Option[Throwable] = None
                                  ) extends RuntimeException(message, cause.orNull) with IRTTransportException


case class IRTUnexpectedHttpStatus(override val status: Status) extends IRTHttpFailureException(s"Unexpected http status: $status", status)
case class IRTNoCredentialsException(override val status: Status) extends IRTHttpFailureException("No valid credentials", status)
case class IRTBadCredentialsException(override val status: Status) extends IRTHttpFailureException("No valid credentials", status)
