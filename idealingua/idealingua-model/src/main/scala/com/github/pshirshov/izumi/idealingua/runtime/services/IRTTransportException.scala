package com.github.pshirshov.izumi.idealingua.runtime.services

trait IRTTransportException

class IRTUnparseableDataException(message: String) extends RuntimeException(message) with IRTTransportException

class IRTTypeMismatchException(message: String, val v: Any) extends RuntimeException(message) with IRTTransportException

class IRTMultiplexingException(message: String, val v: Any) extends RuntimeException(message) with IRTTransportException
