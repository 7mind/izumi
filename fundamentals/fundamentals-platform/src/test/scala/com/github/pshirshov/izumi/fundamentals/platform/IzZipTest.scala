package com.github.pshirshov.izumi.fundamentals.platform

import java.nio.file.Paths

import com.github.pshirshov.izumi.fundamentals.platform.files.IzZip
import com.github.pshirshov.izumi.fundamentals.platform.jvm.IzJvm
import org.scalatest.WordSpec

class IzZipTest extends WordSpec {

  "zip tools" should {
    "be able to find files in jars" in {
      val files = IzJvm.safeClasspathSeq().map(p => Paths.get(p).toFile)
      println(files)
      val maybeObjContent = IzZip.findInZips(files, {
        p =>
          p.toString == Paths.get("/scala/Predef.class").toString
      })
      assert(maybeObjContent.headOption.exists(_._2.nonEmpty))
    }
  }


}


