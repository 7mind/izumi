package com.github.pshirshov.izumi.idealingua.translator.toscala

import scala.meta.{Defn, Stat}

object ScalaMetaTools {
  implicit class DefnExt[T <: Defn](defn: T) {
    def extendDefinition(stats: List[Stat]): T = {
      val extended = defn match {
        case o: Defn.Object =>
          o.copy(templ = o.templ.copy(stats = stats ++ o.templ.stats))
        case o: Defn.Class =>
          o.copy(templ = o.templ.copy(stats = stats ++ o.templ.stats))
        case o: Defn.Trait =>
          o.copy(templ = o.templ.copy(stats = stats ++ o.templ.stats))
      }
      extended.asInstanceOf[T]
    }

  }


}
