package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import com.github.pshirshov.izumi.idealingua.runtime.rpc.IRTTransportException
import org.http4s.Status

case class IRTHttpFailureException(message: String, status: Status, cause: Option[Throwable] = None) extends RuntimeException(message, cause.orNull) with IRTTransportException
