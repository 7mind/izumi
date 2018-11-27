package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.il.loader.LocalModelLoader.{parseDomains, parseModels}
import com.github.pshirshov.izumi.idealingua.il.parser.IDLParser
import com.github.pshirshov.izumi.idealingua.il.renderer.IDLRenderer
import fastparse.core.Parsed
import org.scalatest.WordSpec


class LoaderTest extends WordSpec {

  "IL loader" should {
    "parse all test domains" in {
      val loader = IDLTestTools.makeLoader()
      val defs = IDLTestTools.loadDefs(loader)

      assert(defs.nonEmpty)

      val files = loader.enumerate()
      val domains = parseDomains(files)
      val models = parseModels(files)

      defs.foreach {
        original =>
          val domainId = original.domain.id
          assert(original.domain.nonEmpty, s"$domainId parsed into empty definition")

          val rendered = new IDLRenderer(original.domain).render()
          val restored = IDLParser.parseDomain(rendered)

          restored match {
            case Parsed.Success(r, _) =>
              val typespaces = loader.resolve(domains.updated(domainId, r), models)
              val restoredTypespace = typespaces.find(_.domain.id == domainId).get

              assert(restoredTypespace.domain.types.toSet == original.domain.types.toSet, domainId)
              assert(restoredTypespace.domain.services.toSet == original.domain.services.toSet, domainId)
              assert(restoredTypespace.domain.buzzers.toSet == original.domain.buzzers.toSet, domainId)
              assert(restoredTypespace.domain.streams.toSet == original.domain.streams.toSet, domainId)

            case f =>
              fail(s"Failed to reparse $domainId: $f\nDOMAIN:\n$rendered")
          }

      }
    }
  }


}

