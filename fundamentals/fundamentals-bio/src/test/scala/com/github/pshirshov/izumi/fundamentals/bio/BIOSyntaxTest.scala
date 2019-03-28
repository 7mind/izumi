package com.github.pshirshov.izumi.fundamentals.bio

import com.github.pshirshov.izumi.functional.bio.BIO
import org.scalatest.WordSpec

class BIOSyntaxTest extends WordSpec {

  "BIO.apply is callable" in {
    class X[F[+_, +_]: BIO] {
      def hello = BIO(println("hello world!"))
    }

    assert(new X[scalaz.zio.IO].hello != null)
  }
}
