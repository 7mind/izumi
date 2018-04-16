package com.github.pshirshov.izumi.idealingua.translator.toscala.tools

import scala.meta.{Defn, Init, Stat}

trait ScalaMetaTools {
  implicit class DefnExt[T <: Defn](defn: T) {
    def extendDefinition(stats: Stat*): T = {
      extendDefinition(stats.toList)
    }

    def extendDefinition(stats: List[Stat]): T = {
      val extended = defn match {
        case o: Defn.Object =>
          o.copy(templ = o.templ.copy(stats = o.templ.stats ++ stats))
        case o: Defn.Class =>
          o.copy(templ = o.templ.copy(stats = o.templ.stats ++ stats))
        case o: Defn.Trait =>
          o.copy(templ = o.templ.copy(stats = o.templ.stats ++ stats))
      }
      extended.asInstanceOf[T]
    }

    def prependBase(inits: Init*): T = {
      prependBase(inits.toList)
    }

    def prependBase(inits: List[Init]): T = {
      modifyBase {
        existing =>
          inits ++ existing
      }
    }

    def appendBase(inits: Init*): T = {
      appendBase(inits.toList)
    }

    def appendBase(inits: List[Init]): T = {
      modifyBase {
        existing =>
          existing ++ inits
      }
    }

    def modifyBase(modify: (List[Init]) => List[Init]): T = {
      val extended = defn match {
        case o: Defn.Object =>
          o.copy(templ = o.templ.copy(inits = modify(o.templ.inits)))
        case o: Defn.Class =>
          o.copy(templ = o.templ.copy(inits = modify(o.templ.inits)))
        case o: Defn.Trait =>
          o.copy(templ = o.templ.copy(inits = modify(o.templ.inits)))
      }
      extended.asInstanceOf[T]
    }

  }
}

object ScalaMetaTools extends ScalaMetaTools {

}
