package izumi.idealingua.model.il.ast.raw.defns

sealed trait RawTopLevelDefn

object RawTopLevelDefn {

  sealed trait TypeDefn extends RawTopLevelDefn

  final case class TLDBaseType(v: RawTypeDef.WithId) extends TypeDefn

  final case class TLDService(v: RawService) extends RawTopLevelDefn

  final case class TLDBuzzer(v: RawBuzzer) extends RawTopLevelDefn

  // not supported by cogen yet
  final case class TLDStreams(v: RawStreams) extends RawTopLevelDefn

  final case class TLDConsts(v: RawConstBlock) extends RawTopLevelDefn

  final case class TLDNewtype(v: RawTypeDef.NewType) extends TypeDefn

  final case class TLDDeclared(v: RawTypeDef.DeclaredType) extends TypeDefn

  final case class TLDForeignType(v: RawTypeDef.ForeignType) extends TypeDefn

}
