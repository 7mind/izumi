package com.github.pshirshov.izumi.r2.idealingua.runtime.rpc

trait IRTTransportException

class IRTUnparseableDataException(message: String, cause: Option[Throwable]) extends RuntimeException(message, cause.orNull) with IRTTransportException

class IRTTypeMismatchException(message: String, val v: Any, cause: Option[Throwable]) extends RuntimeException(message, cause.orNull) with IRTTransportException

class IRTMultiplexingException(message: String, val v: Any, cause: Option[Throwable]) extends RuntimeException(message, cause.orNull) with IRTTransportException
