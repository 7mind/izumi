package com.github.pshirshov.izumi.distage.testkit.stest.specs

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import org.scalatest.{ScalatestSuite, WordSpecLike}

abstract class DistagePluginSpec[F[_]](implicit val tagMonoIO: TagK[F]) extends DistagePluginTestSupport[F] with WordSpecLike {

  override def toString: String = ScalatestSuite.suiteToString(None, this)
}


