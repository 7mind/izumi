package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import org.http4s._


trait WithHttp4sLoggingMiddleware {
  this: Http4sContext =>


  protected def loggingMiddle(service: HttpRoutes[CatsIO]): HttpRoutes[CatsIO] = cats.data.Kleisli {
    req: Request[CatsIO] =>
      logger.trace(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}: initiated")

      try {
        service(req).map {
          case Status.Successful(resp) =>
            logger.debug(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}: success, ${resp.status.code -> "code"} ${resp.status.reason -> "reason"}")
            resp
          case resp =>
            logger.info(s"${req.method.name -> "method"} ${req.pathInfo -> "uri"}: rejection, ${resp.status.code -> "code"} ${resp.status.reason -> "reason"}")
            resp
        }
      } catch {
        case cause: Throwable =>
          logger.error(s"${req.method.name -> "method"} ${req.pathInfo -> "path"}: failure, $cause")
          throw cause
      }
  }
}
