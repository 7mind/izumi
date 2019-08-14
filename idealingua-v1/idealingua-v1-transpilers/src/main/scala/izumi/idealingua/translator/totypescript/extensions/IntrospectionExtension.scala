package izumi.idealingua.translator.totypescript.extensions

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import izumi.idealingua.model.il.ast.typed.TypeDef.{DTO, Interface}
import izumi.idealingua.model.il.ast.typed.{Field, TypeDef}
import izumi.idealingua.model.publishing.manifests.TypeScriptProjectLayout
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.totypescript.TSTContext
import izumi.idealingua.translator.totypescript.products.CogenProduct._
import izumi.idealingua.translator.totypescript.types.TypeScriptTypeConverter

object IntrospectionExtension extends TypeScriptTranslatorExtension {
  private def unwindType(id: TypeId)(implicit ts: Typespace): String = id match {
    case Primitive.TBool => "{intro: IntrospectorTypes.Bool}"
    case Primitive.TString => "{intro: IntrospectorTypes.Str}"
    case Primitive.TInt8 => "{intro: IntrospectorTypes.I08}"
    case Primitive.TInt16 => "{intro: IntrospectorTypes.I16}"
    case Primitive.TInt32 => "{intro: IntrospectorTypes.I32}"
    case Primitive.TInt64 => "{intro: IntrospectorTypes.I64}"
    case Primitive.TUInt8 => "{intro: IntrospectorTypes.U08}"
    case Primitive.TUInt16 => "{intro: IntrospectorTypes.U16}"
    case Primitive.TUInt32 => "{intro: IntrospectorTypes.U32}"
    case Primitive.TUInt64 => "{intro: IntrospectorTypes.U64}"
    case Primitive.TFloat => "{intro: IntrospectorTypes.F32}"
    case Primitive.TDouble => "{intro: IntrospectorTypes.F64}"
    case Primitive.TUUID => "{intro: IntrospectorTypes.Uid}"
    case Primitive.TBLOB => ???
    case Primitive.TTime => "{intro: IntrospectorTypes.Time}"
    case Primitive.TDate => "{intro: IntrospectorTypes.Date}"
    case Primitive.TTs => "{intro: IntrospectorTypes.Tsl}"
    case Primitive.TTsTz => "{intro: IntrospectorTypes.Tsz}"
    case Primitive.TTsU => "{intro: IntrospectorTypes.Tsu}"
    case g: Generic => g match {
      case gm: Generic.TMap => s"{intro: IntrospectorTypes.Map, key: ${unwindType(gm.keyType)}, value: ${unwindType(gm.valueType)}} as IIntrospectorMapType"
      case gl: Generic.TList => s"{intro: IntrospectorTypes.List, value: ${unwindType(gl.valueType)}} as IIntrospectorGenericType"
      case gs: Generic.TSet => s"{intro: IntrospectorTypes.Set, value: ${unwindType(gs.valueType)}} as IIntrospectorGenericType"
      case go: Generic.TOption => s"{intro: IntrospectorTypes.Opt, value: ${unwindType(go.valueType)}} as IIntrospectorGenericType"
    }
    case id: DTOId => s"{intro: IntrospectorTypes.Data, full: '${id.path.toPackage.mkString(".") + "." + id.name}'} as IIntrospectorUserType"
    case id: InterfaceId => s"{intro: IntrospectorTypes.Mixin, full: '${id.path.toPackage.mkString(".") + "." + id.name}'} as IIntrospectorUserType"
    case id: AdtId => s"{intro: IntrospectorTypes.Adt, full: '${id.path.toPackage.mkString(".") + "." + id.name}'} as IIntrospectorUserType"
    case id: EnumId => s"{intro: IntrospectorTypes.Enum, full: '${id.path.toPackage.mkString(".") + "." + id.name}'} as IIntrospectorUserType"
    case id: IdentifierId => s"{intro: IntrospectorTypes.Id, full: '${id.path.toPackage.mkString(".") + "." + id.name}'} as IIntrospectorUserType"
    case al: AliasId => unwindType(ts.dealias(al))
    case _ => throw new Exception(s"Unwind type is not implemented for type $id")
  }

  // TODO:MJSON
  private def unwindField(name: String, id: TypeId)(implicit ts: Typespace, conv: TypeScriptTypeConverter): String = {
    s"""{
       |    name: '$name',
       |    accessName: '${conv.safeName(name)}',
       |    type: ${unwindType(id)}
       |}""".stripMargin
  }

  private def unwindAdtMember(name: String, id: TypeId)(implicit ts: Typespace): String = {
    s"""{
       |    name: '$name',
       |    type: ${unwindType(id)}
       |}""".stripMargin
  }

  private def irtImportPath(ctx: TSTContext, id: TypeId): String = {
    if (ctx.manifest.layout == TypeScriptProjectLayout.YARN) {
      s"${ctx.manifest.yarn.scope}/irt"
    } else {
      id.path.toPackage.map(_ => "..").mkString("/") + "/irt"
    }
  }

  override def handleEnum(ctx: TSTContext, enum: TypeDef.Enumeration, product: EnumProduct): EnumProduct = {
//    implicit val ts: Typespace = ctx.typespace
    val pkg = enum.id.path.toPackage.mkString(".")
    val short = enum.id.name
    val full = pkg + "." + short
    val extension =
      s"""
         |// Introspector registration
         |import { Introspector, IntrospectorTypes, IIntrospectorEnumObject } from '${irtImportPath(ctx, enum.id)}';
         |Introspector.register('$full', {
         |        full: '$full',
         |        short: '$short',
         |        package: '$pkg',
         |        type: IntrospectorTypes.Enum,
         |        options: ${short}Helpers.all
         |    } as IIntrospectorEnumObject
         |);
       """.stripMargin

    EnumProduct(product.content + extension, product.preamble)
  }

  override def handleIdentifier(ctx: TSTContext, identifier: TypeDef.Identifier, product: IdentifierProduct): IdentifierProduct = {
    implicit val ts: Typespace = ctx.typespace
    implicit val conv: TypeScriptTypeConverter = ctx.conv

    val short = identifier.id.name
    val extension =
      s"""
         |// Introspector registration
         |import {
         |    Introspector,
         |    IntrospectorTypes,
         |    IIntrospectorUserType,
         |    IIntrospectorGenericType,
         |    IIntrospectorMapType,
         |    IIntrospectorIdObject
         |} from '${irtImportPath(ctx, identifier.id)}';
         |Introspector.register($short.FullClassName, {
         |        full: $short.FullClassName,
         |        short: $short.ClassName,
         |        package: $short.PackageName,
         |        type: IntrospectorTypes.Id,
         |        ctor: () => new $short(),
         |        fields: [
         |${identifier.fields.map(f => unwindField(f.name, f.typeId)).mkString(",\n").shift(12)}
         |        ]
         |    } as IIntrospectorIdObject
         |);
       """.stripMargin

    IdentifierProduct(product.identitier, product.identifierInterface + extension, product.header)
  }

  private def renderDTOIntrospector(name: String, fields: Iterable[Field])(implicit ts: Typespace, conv: TypeScriptTypeConverter): String = {
    s"""Introspector.register($name.FullClassName, {
       |        full: $name.FullClassName,
       |        short: $name.ClassName,
       |        package: $name.PackageName,
       |        type: IntrospectorTypes.Data,
       |        ctor: () => new $name(),
       |        fields: [
       |${fields.map(f => unwindField(f.name, f.typeId)).mkString(",\n").shift(12)}
       |        ]
       |    } as IIntrospectorDataObject
       |);
     """.stripMargin
  }

  override def handleDTO(ctx: TSTContext, dto: DTO, product: CompositeProduct): CompositeProduct = {
    implicit val ts: Typespace = ctx.typespace
    implicit val conv: TypeScriptTypeConverter = ctx.conv

    val short = dto.id.name
    val fields = ts.structure.structure(dto.id).all.groupBy(_.field.name).map(_._2.head.field)

    val extension =
      s"""
         |// Introspector registration
         |import {
         |    Introspector,
         |    IntrospectorTypes,
         |    IIntrospectorUserType,
         |    IIntrospectorGenericType,
         |    IIntrospectorMapType,
         |    IIntrospectorDataObject
         |} from '${irtImportPath(ctx, dto.id)}';
         |${renderDTOIntrospector(short, fields)}
       """.stripMargin

    CompositeProduct(product.more + extension, product.header, product.preamble)
  }

  override def handleInterface(ctx: TSTContext, interface: Interface, product: InterfaceProduct): InterfaceProduct = {
    implicit val ts: Typespace = ctx.typespace
    implicit val conv: TypeScriptTypeConverter = ctx.conv

    val short = interface.id.name
    val pkg = interface.id.path.toPackage.mkString(".")
    val full = s"$pkg.$short"

    val fields = interface.struct.fields
    val implId = ts.tools.implId(interface.id)
    val eid = interface.id.name + implId.name

    val extension =
      s"""
         |// Introspector registration
         |import {
         |    Introspector,
         |    IntrospectorTypes,
         |    IIntrospectorUserType,
         |    IIntrospectorGenericType,
         |    IIntrospectorMapType,
         |    IIntrospectorMixinObject,
         |    IIntrospectorDataObject
         |} from '${irtImportPath(ctx, interface.id)}';
         |Introspector.register('$full', {
         |        full: '$full',
         |        short: '$short',
         |        package: '$pkg',
         |        type: IntrospectorTypes.Mixin,
         |        ctor: () => new $eid(),
         |        fields: [
         |${fields.map(f => unwindField(f.name, f.typeId)).mkString(",\n").shift(12)}
         |        ],
         |        implementations: $eid.getRegisteredTypes
         |    } as IIntrospectorMixinObject
         |);
         |${renderDTOIntrospector(eid, fields)}
       """.stripMargin

    InterfaceProduct(product.iface, product.companion + extension, product.header, product.preamble)
  }

  override def handleAdt(ctx: TSTContext, adt: TypeDef.Adt, product: AdtProduct): AdtProduct = {
    implicit val ts: Typespace = ctx.typespace
    val pkg = adt.id.path.toPackage.mkString(".")
    val short = adt.id.name
    val full = pkg + "." + short

    val extension =
      s"""
         |// Introspector registration
         |import {
         |    Introspector,
         |    IntrospectorTypes,
         |    IIntrospectorUserType,
         |    IIntrospectorGenericType,
         |    IIntrospectorMapType,
         |    IIntrospectorAdtObject
         |} from '${irtImportPath(ctx, adt.id)}';
         |Introspector.register('$full', {
         |        full: '$full',
         |        short: '$short',
         |        package: '$pkg',
         |        type: IntrospectorTypes.Adt,
         |        options: [
         |${adt.alternatives.map(f => unwindAdtMember(f.wireId, f.typeId)).mkString(",\n").shift(12)}
         |        ]
         |    } as IIntrospectorAdtObject
         |);
       """.stripMargin

    AdtProduct(product.content + extension, product.header, product.preamble)
  }
}
