package com.github.pshirshov.izumi.idealingua.runtime.services

trait TransportException

class UnparseableDataException(message: String) extends RuntimeException(message) with TransportException

class TypeMismatchException(message: String, val v: Any) extends RuntimeException(message) with TransportException

class MultiplexingException(message: String, val v: Any) extends RuntimeException(message) with TransportException
