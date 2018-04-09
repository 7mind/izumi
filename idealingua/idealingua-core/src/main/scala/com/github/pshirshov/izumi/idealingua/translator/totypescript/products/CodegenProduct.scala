package com.github.pshirshov.izumi.idealingua.translator.totypescript.products

object CogenProduct {
  case class InterfaceProduct(iface: String = ""
                              , companion: String = ""
                              , header: String = ""
                              , preamble: String = ""
                             ) extends RenderableCogenProduct {
    def render: List[String] = {
      (iface + "\n" + companion).split("\n").toList
    }

    def renderHeader: List[String] = {
      header.split("\n").toList
    }
  }

  case class CompositeProduct(more: String = ""
                              , header: String = ""
                              , preamble: String = ""
                             ) extends RenderableCogenProduct {
    def render: List[String] = {
      more.split("\n").toList
    }

    def renderHeader: List[String] = {
      header.split("\n").toList
    }
  }

  case class IdentifierProduct(identitier: String = ""
                               , identifierInterface: String = ""
                               , header: String = ""
                               , preamble: String = ""
                              ) extends RenderableCogenProduct {
    def render: List[String] = {
      (identitier + "\n" + identifierInterface).split("\n").toList
    }

    def renderHeader: List[String] = {
      header.split("\n").toList
    }
  }

  case class ServiceProduct(client: String = ""
                            , header: String = ""
                            , preamble: String = ""
                           ) extends RenderableCogenProduct {
    def render: List[String] = {
      client.split("\n").toList
    }

    def renderHeader: List[String] = {
      header.split("\n").toList
    }
  }

  case class EnumProduct(content: String = ""
                        , preamble: String = ""
                        ) extends RenderableCogenProduct {
    def render: List[String] = {
      content.split("\n").toList
    }

    def renderHeader: List[String] = {
      List()
    }
  }

  case class AliasProduct(content: String = ""
                          , header: String = ""
                              , preamble: String = ""
                             ) extends RenderableCogenProduct {
    def render: List[String] = {
      content.split("\n").toList
    }

    def renderHeader: List[String] = {
      header.split("\n").toList
    }
  }

  case class AdtProduct(content: String = ""
                        , header: String = ""
                          , preamble: String = ""
                         ) extends RenderableCogenProduct {
    def render: List[String] = {
      content.split("\n").toList
    }

    def renderHeader: List[String] = {
      header.split("\n").toList
    }
  }
}
