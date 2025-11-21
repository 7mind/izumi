package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{CustomContext, Entry, Level, Message}
import logstage.UnsafeLogIO

import scala.annotation.unused
import scala.language.implicitConversions

trait AbstractLogIO[F[_]] extends UnsafeLogIO[F] {
  type Self[f[_]] <: AbstractLogIO[f]

  type EncMode

  def log(entry: Entry): F[Unit]
  def log(logLevel: Level)(messageThunk: => Message)(implicit pos: CodePositionMaterializer): F[Unit]

  def withCustomContext(context: CustomContext): Self[F]
  final def apply(context: CustomContext): Self[F] = withCustomContext(context)

  override def widen[G[_]](implicit @unused ev: F[Unit] <:< G[Unit]): AbstractLogIO[G] = this.asInstanceOf[AbstractLogIO[G]]
}

object AbstractLogIO extends LowPriorityAbstractLogIOInstances {
  final class SyntaxWidenError[F[+_, +_], E, Self0[f[_]]](private[AbstractLogIO] val logIO: AbstractLogIO[F[E, _]] { type Self[f[_]] = Self0[f] }) extends AnyVal {
    def widenError[E1](implicit @unused ev: E <:< E1): Self0[F[E1, _]] = logIO.asInstanceOf[Self0[F[E1, _]]]
  }

  @inline implicit final def SyntaxWidenError[F[+_, +_], E, Self0[f[_]]](logIO: AbstractLogIO[F[E, _]] { type Self[f[_]] = Self0[f] }): SyntaxWidenError[F, E, Self0] = {
    new SyntaxWidenError[F, E, Self0](logIO)
  }
}

sealed trait LowPriorityAbstractLogIOInstances {
  // workaround for inference issues with `E=Nothing`
  @inline implicit final def SyntaxWidenErrorNothing[F[+_, +_]](
    logIO: AbstractLogIO[F[Nothing, _]]
  ): AbstractLogIO.SyntaxWidenError[F, Nothing, logIO.Self] = {
    new AbstractLogIO.SyntaxWidenError[F, Nothing, logIO.Self](logIO)
  }
}
