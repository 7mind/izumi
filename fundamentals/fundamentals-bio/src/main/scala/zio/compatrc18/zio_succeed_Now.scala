package zio.compatrc18

object zio_succeed_Now {
  @inline def succeedNow[A](a: A): zio.ZIO[Any, Nothing, A] = new zio.ZIO.Succeed[A](a)
}
