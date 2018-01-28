package com.github.pshirshov.izumi.fundamentals.platform.futures

import com.github.pshirshov.izumi.fundamentals.platform.futures.IzFuture.SafeFuture

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import scala.language.implicitConversions

class IzFuture[T](value: Future[T]) {
  def safe(implicit ec: ExecutionContext): SafeFuture[T] =
    value
      .map(Success(_))
      .recover { case x => Failure(x) }
}

object IzFuture {
  type SafeFuture[T] = Future[Try[T]]

  implicit def toRich[T](value: Future[T]): IzFuture[T] = new IzFuture(value)
}


