package zio._izumicompat_

object __ZIOFiberContext {
  type FiberContext[E, A] = zio.internal.FiberContext[E, A]
}
