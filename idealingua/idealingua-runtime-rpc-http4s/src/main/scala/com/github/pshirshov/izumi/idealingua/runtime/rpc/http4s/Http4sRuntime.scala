package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import _root_.io.circe.Printer
import cats.effect.{ConcurrentEffect, Timer}
import com.github.pshirshov.izumi.functional.bio.{BIOAsync, BIORunner, CIORunner}
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.http4s.dsl._

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class Http4sRuntime[_BiIO[+ _, + _] : BIOAsync : BIORunner, _CatsIO[+ _] : ConcurrentEffect : CIORunner : Timer]
(
  override val logger: IzLogger
  , override val clientExecutionContext: ExecutionContext
)
  extends Http4sContext {

  override type BiIO[+E, +V] = _BiIO[E, V]

  override type CatsIO[+T] = _CatsIO[T]

  override val BIO: BIOAsync[BiIO] = implicitly

  override val CIO: ConcurrentEffect[CatsIO] = implicitly

  override val CIOT: Timer[CatsIO] = implicitly

  override val CIORunner: CIORunner[_CatsIO] = implicitly

  override val BIORunner: BIORunner[BiIO] = implicitly

  override val dsl: Http4sDsl[CatsIO] = Http4sDsl.apply[CatsIO]

  override def printer: Printer = Printer.noSpaces.copy(dropNullValues = true)

}
