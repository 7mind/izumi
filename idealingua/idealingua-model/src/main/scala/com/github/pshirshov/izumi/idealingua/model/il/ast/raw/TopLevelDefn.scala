package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw


sealed trait TopLevelDefn


final case class ILInclude(i: String) extends TopLevelDefn

object TopLevelDefn {


  sealed trait TypeDefn extends TopLevelDefn

  final case class TLDBaseType(v: IdentifiedRawTypeDef) extends TypeDefn

  final case class TLDService(v: raw.Service) extends TopLevelDefn

  final case class TLDBuzzer(v: raw.Buzzer) extends TopLevelDefn

  // not supported by cogen yet
  final case class TLDStreams(v: raw.Streams) extends TopLevelDefn

  final case class TLDConsts(v: raw.Constants) extends TopLevelDefn

  final case class TLDNewtype(v: raw.RawTypeDef.NewType) extends TypeDefn

  final case class TLDForeignType(v: raw.RawTypeDef.ForeignType) extends TypeDefn

}
