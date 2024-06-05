package izumi.distage.reflection.macros.universe.basicuniverse

class PortableNamedArg(val u: scala.reflect.api.Universe) {
  import u.*
  object NArg {
    def unapply(tree: TreeApi): Option[(String, Any)] = {
      (tree: @unchecked) match {
        case NamedArg(Ident(TermName(name)), Literal(Constant(c))) =>
          Some((name, c))
      }
    }
  }
}
