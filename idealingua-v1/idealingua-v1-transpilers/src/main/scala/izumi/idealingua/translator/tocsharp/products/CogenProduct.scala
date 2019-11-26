package izumi.idealingua.translator.tocsharp.products

object CogenProduct {
  final case class InterfaceProduct(iface: String = ""
                                    , companion: String = ""
                                    , header: String = ""
                                    , tests: String = ""
                                   ) extends RenderableCogenProduct {
    def render: List[String] = {
      (iface + "\n" + companion).split('\n').toList
    }

    def renderHeader: List[String] = {
      header.split('\n').toList
    }

    def renderTests: List[String] = {
      tests.split('\n').toList
    }
  }

  final case class CompositeProduct(more: String = ""
                                    , header: String = ""
                                    , tests: String = ""
                                   ) extends RenderableCogenProduct {
    def render: List[String] = {
      more.split('\n').toList
    }

    def renderHeader: List[String] = {
      header.split('\n').toList
    }

    def renderTests: List[String] = {
      tests.split('\n').toList
    }
  }

  final case class IdentifierProduct(identitier: String = ""
                                     , header: String = ""
                                     , tests: String = ""
                                    ) extends RenderableCogenProduct {
    def render: List[String] = {
      identitier.split('\n').toList
    }

    def renderHeader: List[String] = {
      header.split('\n').toList
    }

    def renderTests: List[String] = {
      tests.split('\n').toList
    }
  }

  final case class ServiceProduct(client: String = ""
                                  , header: String = ""
                                 ) extends RenderableCogenProduct {
    def render: List[String] = {
      client.split('\n').toList
    }

    def renderHeader: List[String] = {
      header.split('\n').toList
    }

    def renderTests: List[String] = {
      List.empty
    }
  }

  final case class BuzzerProduct(client: String = ""
                                 , header: String = ""
                                 ) extends RenderableCogenProduct {
    def render: List[String] = {
      client.split('\n').toList
    }

    def renderHeader: List[String] = {
      header.split('\n').toList
    }

    def renderTests: List[String] = {
      List.empty
    }
  }

  final case class EnumProduct(content: String = ""
                               , header: String = ""
                               , tests: String = ""
                              ) extends RenderableCogenProduct {
    def render: List[String] = {
      content.split('\n').toList
    }

    def renderHeader: List[String] = {
      header.split('\n').toList
    }

    def renderTests: List[String] = {
      tests.split('\n').toList
    }
  }

  final case class AliasProduct(content: String = ""
                                , header: String = ""
                               ) extends RenderableCogenProduct {
    def render: List[String] = {
      content.split('\n').toList
    }

    def renderHeader: List[String] = {
      header.split('\n').toList
    }

    def renderTests: List[String] = {
      List.empty
    }
  }

  final case class AdtProduct(content: String = ""
                              , header: String = ""
                              , tests: String = ""
                             ) extends RenderableCogenProduct {
    def render: List[String] = {
      content.split('\n').toList
    }

    def renderHeader: List[String] = {
      header.split('\n').toList
    }

    def renderTests: List[String] = {
      tests.split('\n').toList
    }
  }
}
