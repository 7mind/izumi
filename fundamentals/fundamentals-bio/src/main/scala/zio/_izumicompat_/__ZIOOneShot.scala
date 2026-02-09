package zio._izumicompat_

object __ZIOOneShot {
  type OneShot[A] = zio.internal.OneShot[A]
  val OneShot: zio.internal.OneShot.type = zio.internal.OneShot
}
