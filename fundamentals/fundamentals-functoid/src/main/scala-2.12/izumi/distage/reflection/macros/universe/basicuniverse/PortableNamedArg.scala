package izumi.distage.reflection.macros.universe.basicuniverse

class PortableNamedArg[U <: scala.reflect.api.Universe & Singleton](val u: U) {
  import u.*
  object NArg {
    def unapply(tree: TreeApi): Option[(String, Any)] = {
      (tree: @unchecked) match {
        case AssignOrNamedArg(Ident(TermName(name)), Literal(Constant(c))) =>
          Some((name, c))
        case _ =>
          None
      }
    }
  }
}
