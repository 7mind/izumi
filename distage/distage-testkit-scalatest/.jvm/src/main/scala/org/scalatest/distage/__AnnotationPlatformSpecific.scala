package org.scalatest.distage

import scala.annotation.Annotation

private[distage] object __AnnotationPlatformSpecific {
  // dummy annotation, don't use portable-scala-reflect on JVM
  final class EnableReflectiveInstantiation() extends Annotation
}
