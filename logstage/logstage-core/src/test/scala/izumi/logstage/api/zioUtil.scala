package izumi.logstage.api

import izumi.functional.bio.TypedError
import zio.{FiberFailure, Runtime, Unsafe, ZIO}

object zioUtil {

  def runZIO[A](thunk: ZIO[Any, Any, A]): A = {
    try Unsafe.unsafe(implicit unsafe => Runtime.default.unsafe.run(thunk).getOrThrowFiberFailure())
    catch {
      case f: FiberFailure => throw f.cause.squashWith(TypedError.wrapIfNotThrowable)
    }
  }

}
