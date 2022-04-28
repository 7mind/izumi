package izumi.fundamentals.platform

sealed trait ScalaPlatform {}

object ScalaPlatform {
  sealed trait AbstractJVM extends ScalaPlatform
  case object JVM extends AbstractJVM
  case object GraalVMNativeImage extends AbstractJVM

  sealed trait Foreign extends ScalaPlatform
  case object Native extends Foreign
  case object Js extends Foreign
}
