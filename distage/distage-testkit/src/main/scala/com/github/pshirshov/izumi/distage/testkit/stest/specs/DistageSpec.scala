package com.github.pshirshov.izumi.distage.testkit.stest.specs

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.testkit.DistageTestSupport
import org.scalatest.{ScalatestSuite, WordSpecLike}

abstract class DistageSpec[F[_] : TagK] extends DistageTestSupport[F] with WordSpecLike {
  override def toString: String = ScalatestSuite.suiteToString(None, this)
}



