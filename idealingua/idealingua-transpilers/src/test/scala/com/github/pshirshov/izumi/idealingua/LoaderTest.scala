package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.il.loader.model.UnresolvedDomains
import com.github.pshirshov.izumi.idealingua.il.renderer.IDLRenderer
import org.scalatest.WordSpec


class LoaderTest extends WordSpec {

  "IL loader" should {
    "parse all test domains" in {
      val loader = IDLTestTools.makeLoader()
      val defs = IDLTestTools.loadDefs(loader)

      assert(defs.nonEmpty)

      defs.foreach {
        original =>
          val domainId = original.typespace.domain.id
          assert(original.typespace.domain.nonEmpty, s"$domainId parsed into empty definition (location: ${original.path})")
      }

      val files = loader.enumerator.enumerate()

      defs.foreach {
        original =>
          val ts = original.typespace
          val domainId = ts.domain.id
          val rendered = new IDLRenderer(ts.domain).render()

          val updated = files.updated(original.path, rendered)

          // TODO: ModelLoaderImpl
          val domains = loader.parser.parseDomains(updated)
          val models = loader.parser.parseModels(updated)
          val unresolved = UnresolvedDomains(domains, models)
          val typespaces = loader.resolver.resolve(unresolved).throwIfFailed().successful
          val restoredTypespace = typespaces.find(_.typespace.domain.id == domainId).get.typespace

          assert(restoredTypespace.domain.types.toSet == ts.domain.types.toSet, domainId)
          assert(restoredTypespace.domain.services.toSet == ts.domain.services.toSet, domainId)
          assert(restoredTypespace.domain.buzzers.toSet == ts.domain.buzzers.toSet, domainId)
          assert(restoredTypespace.domain.streams.toSet == ts.domain.streams.toSet, domainId)
      }
    }
  }


}

