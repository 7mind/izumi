package com.github.pshirshov.izumi.idealingua.runtime.services

import scala.concurrent.Future
import scala.language.{higherKinds, implicitConversions}
import scala.util.Try

trait ServiceResultTransformer[R1[_], R2[_]] {
  def transform[A](r: R1[A]): R2[A]
}

object ServiceResultTransformer {
  implicit val transformId: ServiceResultTransformer[ServiceResult.Id, ServiceResult.Id] = new ServiceResultTransformerId[ServiceResult.Id]
  implicit val transformTry: ServiceResultTransformer[Try, Try] = new ServiceResultTransformerId[Try]
  implicit val transformOption: ServiceResultTransformer[Option, Option] = new ServiceResultTransformerId[Option]
  implicit val transformFuture: ServiceResultTransformer[Future, Future] = new ServiceResultTransformerId[Future]

  class ServiceResultTransformerId[R[_]] extends ServiceResultTransformer[R, R] {
    override def transform[A](r: R[A]): R[A] = r
  }

}
