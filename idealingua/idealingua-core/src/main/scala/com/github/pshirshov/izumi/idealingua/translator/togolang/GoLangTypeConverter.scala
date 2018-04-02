package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, Generic, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.ILAst
import com.github.pshirshov.izumi.idealingua.model.il.structures.{PlainStruct, Struct}

object GoLangTypeConverter {
  def toGoLang(id: TypeId): GoLangType = id match {
    case t: Generic => toGeneric(t)
    case t: Primitive => toPrimitive(t)
    case _ => new GoLangType{
      override def render(): String = s"${id.name}"
    }
  }

  private def toPrimitive(id: Primitive): GoLangType = id match {
    case Primitive.TBool =>
      Bool()
    case Primitive.TString =>
      StringType()
    case Primitive.TInt8 =>
      Int8()
    case Primitive.TInt16 =>
      Int16()
    case Primitive.TInt32 =>
      Int32()
    case Primitive.TInt64 =>
      Int64()
    case Primitive.TFloat =>
      Float32()
    case Primitive.TDouble =>
      Float64()
    case Primitive.TUUID =>
      ListType(of = Some(Byte()))
    case Primitive.TTsTz =>
      Time()
    case Primitive.TTs =>
      Time()
    case Primitive.TTime =>
      Time()
    case Primitive.TDate =>
      Time()
  }

  private def toGeneric(typeId: Generic): GoLangType = typeId match {
    case _: Generic.TSet =>
      ListType(None)
    case _: Generic.TMap =>
      MapType(None)
    case _: Generic.TList =>
      ListType(None)
    case _: Generic.TOption =>
      ListType(None) // TODO: introduce an Option type to Go
  }

  implicit class FieldsOps(fields: List[ExtendedField]) {
    def toGoLang: GoLangFields = {
      GoLangFields(
        fields.map(f => toGoLang(f.field))
      )
    }

    private def toGoLang(field: ILAst.Field): GoLangField = {
      GoLangField(field.name, GoLangTypeConverter.toGoLang(field.typeId))
    }
  }

  implicit class StructOps(struct: Struct) {
    def toGoLangFields: GoLangFields = {
      struct.all.toGoLang
    }
  }

  implicit class PlainStructOps(struct: PlainStruct) {
    def toGoLangFields: GoLangFields = {
      struct.all.toGoLang
    }
  }
}
