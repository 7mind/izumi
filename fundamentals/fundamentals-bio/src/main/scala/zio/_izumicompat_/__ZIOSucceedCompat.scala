package zio._izumicompat_

object __ZIOSucceedCompat {
  /** Avoid calling [[zio.ZIO.succeed]] directly, because it's causing problems on Scala 3: https://github.com/scala/scala3/issues/23924 */
  def zioSucceed[A](a: => A)(implicit trace: zio.Trace) = {
    zio.ZIO.succeed(a)(using trace)
  }
}
