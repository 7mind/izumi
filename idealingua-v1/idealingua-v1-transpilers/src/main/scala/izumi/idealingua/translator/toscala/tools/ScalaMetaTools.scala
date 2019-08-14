package izumi.idealingua.translator.toscala.tools

import scala.meta.{Defn, Init, Stat}

trait ScalaMetaTools {
  implicit class DefnExt[T <: Defn](defn: T) {
    def prependDefnitions(stats: Stat*): T = {
      prependDefnitions(stats.toList)
    }

    def prependDefnitions(stats: List[Stat]): T = {
      modifyDefinitions {
        existing =>
          stats ++ existing
      }
    }

    def appendDefinitions(stats: Stat*): T = {
      appendDefinitions(stats.toList)
    }

    def appendDefinitions(stats: List[Stat]): T = {
      modifyDefinitions {
        existing =>
          existing ++ stats
      }
    }


    def modifyDefinitions(modify: (List[Stat]) => List[Stat]): T = {
      val extended = defn match {
        case o: Defn.Object =>
          o.copy(templ = o.templ.copy(stats = modify(o.templ.stats)))
        case o: Defn.Class =>
          o.copy(templ = o.templ.copy(stats = modify(o.templ.stats)))
        case o: Defn.Trait =>
          o.copy(templ = o.templ.copy(stats = modify(o.templ.stats)))
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
