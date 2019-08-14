package izumi.idealingua.translator.toscala.layout

sealed trait Scope

object Scope {

  case object ThisBuild extends Scope

  case object Project extends Scope

}
