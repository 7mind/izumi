package com.github.pshirshov.izumi.idealingua.runtime.services

import scala.concurrent.Future
import scala.language.higherKinds
import scala.util.Try

trait IRTServiceResultTransformer[R1[_], R2[_]] {
  def transform[A](r: R1[A]): R2[A]
}

object IRTServiceResultTransformer {
  implicit val transformId: IRTServiceResultTransformer[IRTServiceResult.Id, IRTServiceResult.Id] = new ServiceResultTransformerId[IRTServiceResult.Id]
  implicit val transformTry: IRTServiceResultTransformer[Try, Try] = new ServiceResultTransformerId[Try]
  implicit val transformOption: IRTServiceResultTransformer[Option, Option] = new ServiceResultTransformerId[Option]
  implicit val transformFuture: IRTServiceResultTransformer[Future, Future] = new ServiceResultTransformerId[Future]

  class ServiceResultTransformerId[R[_]] extends IRTServiceResultTransformer[R, R] {
    override def transform[A](r: R[A]): R[A] = r
  }

}
