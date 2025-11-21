package izumi.distage.model.reflection

import izumi.fundamentals.platform.language.Quirks.Discarder

sealed trait SetKeyMeta {
  def repr(render: DIKey => String): String
}

object SetKeyMeta {
  case object NoMeta extends SetKeyMeta {
    override def repr(render: DIKey => String): String = {
      render.discard()
      ""
    }
  }

  // disambiguator is only used in comparisons
  final case class WithImpl(disambiguator: AnyRef) extends SetKeyMeta {
    override def repr(render: DIKey => String): String = {
      render.discard()
      s"#impl:${disambiguator.hashCode}"
    }
  }
  final case class WithAutoset(base: DIKey) extends SetKeyMeta {
    override def repr(render: DIKey => String): String = {
      s"#autoset:${render(base)}"
    }
  }
}
