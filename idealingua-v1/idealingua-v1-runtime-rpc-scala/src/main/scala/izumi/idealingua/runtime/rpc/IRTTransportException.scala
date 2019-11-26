package izumi.idealingua.runtime.rpc

trait IRTTransportException

class IRTUnparseableDataException(message: String, cause: Option[Throwable] = None) extends RuntimeException(message, cause.orNull) with IRTTransportException

class IRTDecodingException(message: String, cause: Option[Throwable] = None) extends RuntimeException(message, cause.orNull) with IRTTransportException

class IRTTypeMismatchException(message: String, val v: Any, cause: Option[Throwable] = None) extends RuntimeException(message, cause.orNull) with IRTTransportException

class IRTMissingHandlerException(message: String, val v: Any, cause: Option[Throwable] = None) extends RuntimeException(message, cause.orNull) with IRTTransportException

class IRTLimitReachedException(message: String, cause: Option[Throwable] = None) extends RuntimeException(message, cause.orNull) with IRTTransportException

class IRTUnathorizedRequestContextException(message: String, cause: Option[Throwable] = None) extends RuntimeException(message, cause.orNull) with IRTTransportException

class IRTGenericFailure(message: String, cause: Option[Throwable] = None) extends RuntimeException(message, cause.orNull) with IRTTransportException

