package org.bitbucket.pshirshov.izumi.distage.definition

import org.bitbucket.pshirshov.izumi.distage.Tag

case class With[T:Tag]() extends scala.annotation.StaticAnnotation
