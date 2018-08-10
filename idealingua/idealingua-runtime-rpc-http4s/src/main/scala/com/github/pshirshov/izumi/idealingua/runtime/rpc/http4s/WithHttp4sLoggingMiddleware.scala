package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import org.http4s._


trait WithHttp4sLoggingMiddleware {
  this: Http4sContext =>


  protected def loggingMiddle(service: HttpRoutes[CIO]): HttpRoutes[CIO] = cats.data.Kleisli {
    req: Request[CIO] =>
      logger.trace(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}")

      try {
        service(req).map {
          case Status.Successful(resp) =>
            logger.debug(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}")
            resp
          case resp =>
            logger.info(s"${req.method.name -> "method"} ${req.pathInfo -> "uri"} => ${resp.status.code -> "code"} ${resp.status.reason -> "reason"}")
            resp
        }
      } catch {
        case t: Throwable =>
          logger.error(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}: failed to handle request: $t")
          throw t
      }
  }
}
