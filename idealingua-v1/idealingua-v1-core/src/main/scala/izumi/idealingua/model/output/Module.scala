package izumi.idealingua.model.output

final case class Module(id: ModuleId, content: String, meta: Map[String, String] = Map.empty)
