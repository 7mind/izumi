package com.github.pshirshov.izumi.fundamentals.platform

import java.nio.file.Paths

import com.github.pshirshov.izumi.fundamentals.platform.files.IzZip
import com.github.pshirshov.izumi.fundamentals.platform.jvm.IzJvm
import org.scalatest.WordSpec

class IzZipTest extends WordSpec {

  "zip tools" should {
    "be able to find files in jars" in {
      val maybeObjContent = IzZip.findInZips(Paths.get("java/lang/Object.class"), IzJvm.safeClasspathSeq(this.getClass.getClassLoader).map(p => Paths.get(p).toFile))
//      assert(maybeObjContent.exists(_.nonEmpty))
    }
  }


}
