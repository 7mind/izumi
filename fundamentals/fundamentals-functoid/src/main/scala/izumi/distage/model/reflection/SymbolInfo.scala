package izumi.distage.model.reflection

final case class SymbolInfo(
  name: String,
  finalResultType: SafeType,
  // no custom annotation support since 0.10.0... please comment on github issue https://github.com/7mind/izumi/issues/906 if desired!
  // annotations: List[u.Annotation]
  isByName: Boolean,
  wasGeneric: Boolean,
)
