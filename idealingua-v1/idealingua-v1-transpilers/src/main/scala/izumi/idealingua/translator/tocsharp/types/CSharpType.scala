package izumi.idealingua.translator.tocsharp.types

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import izumi.idealingua.model.problems.IDLException
import izumi.idealingua.model.il.ast.typed.TypeDef.{Enumeration, _}
import izumi.idealingua.model.il.ast.typed.{NodeMeta, Structure, Super}
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.tocsharp.CSharpImports

final case class CSharpType (
                              id: TypeId)(implicit im: CSharpImports, ts: Typespace) {

  def isNative: Boolean = isNativeImpl(id)
  def isNullable: Boolean = isNullableImpl(id)
  def defaultValue: String = getDefaultValue(id)
  def getInitValue: Option[String] = getInitValue(id)
  def getRandomValue(depth: Int = 0): String = getRandomValue(id, depth)

  def isNullableImpl(id: TypeId): Boolean = id match {
    case g: Generic => g match {
      case _: Generic.TMap => true
      case _: Generic.TList => true
      case _: Generic.TSet => true
      case _: Generic.TOption => true
    }
    case p: Primitive => p match {
      case Primitive.TBool => false
      case Primitive.TString => true
      case Primitive.TInt8 => false
      case Primitive.TInt16 => false
      case Primitive.TInt32 => false
      case Primitive.TInt64 => false
      case Primitive.TUInt8 => false
      case Primitive.TUInt16 => false
      case Primitive.TUInt32 => false
      case Primitive.TUInt64 => false
      case Primitive.TFloat => false
      case Primitive.TDouble => false
      case Primitive.TUUID => false
      case Primitive.TTime => false
      case Primitive.TDate => false
      case Primitive.TTs => false
      case Primitive.TTsTz => false
      case Primitive.TTsU => false
      case Primitive.TBLOB => ???
    }
    case _ => id match {
      case _: EnumId => false
      case _: InterfaceId => true
      case _: IdentifierId => true
      case _: AdtId | _: DTOId => true
      case al: AliasId => isNullableImpl(ts.dealias(al))
      case _ => throw new IDLException(s"Impossible isNullableImpl type: ${id.name}")
    }
  }

  private def isNativeImpl(id: TypeId): Boolean = id match {
    case g: Generic => g match {
      case _: Generic.TMap => true
      case _: Generic.TList => true
      case _: Generic.TSet => true
      case _: Generic.TOption => true
    }
    case p: Primitive => p match {
      case Primitive.TBool => true
      case Primitive.TString => true
      case Primitive.TInt8 => true
      case Primitive.TInt16 => true
      case Primitive.TInt32 => true
      case Primitive.TInt64 => true
      case Primitive.TUInt8 => true
      case Primitive.TUInt16 => true
      case Primitive.TUInt32 => true
      case Primitive.TUInt64 => true
      case Primitive.TFloat => true
      case Primitive.TDouble => true
      case Primitive.TUUID => true
      case Primitive.TTime => true
      case Primitive.TDate => true
      case Primitive.TTs => true
      case Primitive.TTsTz => true
      case Primitive.TTsU => true
      case Primitive.TBLOB => true
    }
    case _ => id match {
      case _: EnumId => true
      case _: InterfaceId => true
      case _: IdentifierId => true
      case _: AdtId | _: DTOId => true
      case al: AliasId => isNativeImpl(ts.dealias(al))
      case _ => throw new IDLException(s"Impossible isNativeImpl type: ${id.name}")
    }
  }

  private def getDefaultValue(id: TypeId): String = id match {
    case g: Generic => g match {
      case _: Generic.TMap => "null"
      case _: Generic.TList => "null"
      case _: Generic.TSet => "null"
      case _: Generic.TOption => "null"
    }
    case p: Primitive => p match {
      case Primitive.TBool => "false"
      case Primitive.TString => "null"
      case Primitive.TInt8 => "0"
      case Primitive.TInt16 => "0"
      case Primitive.TInt32 => "0"
      case Primitive.TInt64 => "0"
      case Primitive.TUInt8 => "0"
      case Primitive.TUInt16 => "0"
      case Primitive.TUInt32 => "0"
      case Primitive.TUInt64 => "0"
      case Primitive.TFloat => "0.0f"
      case Primitive.TDouble => "0.0"
      case Primitive.TUUID => "null"
      case Primitive.TTime => "null"
      case Primitive.TDate => "0"
      case Primitive.TTs => "0"
      case Primitive.TTsTz => "0"
      case Primitive.TTsU => "0"
      case Primitive.TBLOB => "null"
    }
    case _ => id match {
      case e: EnumId => s"${e.name}.${ts(e).asInstanceOf[Enumeration].members.head.value}"
      case _: InterfaceId => "null"
      case _: IdentifierId => "null"
      case _: AdtId | _: DTOId => "null"
      case al: AliasId => getDefaultValue(ts.dealias(al))
      case _ => throw new IDLException(s"Impossible getDefaultValue type: ${id.name}")
    }
  }

  private def getInitValue(id: TypeId): Option[String] = id match {
    case g: Generic => g match {
      case _: Generic.TMap => Some(s"new ${renderType(withPackage = true)}()")
      case _: Generic.TList => Some(s"new ${renderType(withPackage = true)}()")
      case _: Generic.TSet => Some(s"new ${renderType(withPackage = true)}()")
      case _: Generic.TOption => None
    }
    case p: Primitive => p match {
      case Primitive.TBool => None
      case Primitive.TString => None
      case Primitive.TInt8 => None
      case Primitive.TInt16 => None
      case Primitive.TInt32 => None
      case Primitive.TInt64 => None
      case Primitive.TUInt8 => None
      case Primitive.TUInt16 => None
      case Primitive.TUInt32 => None
      case Primitive.TUInt64 => None
      case Primitive.TFloat => None
      case Primitive.TDouble => None
      case Primitive.TUUID => None
      case Primitive.TTime => None
      case Primitive.TDate => None
      case Primitive.TTs => None
      case Primitive.TTsTz => None
      case Primitive.TTsU => None
      case Primitive.TBLOB => None
    }
    case _ => id match {
      case _: EnumId => None
      case _: InterfaceId => None
      case _: IdentifierId => None
      case _: AdtId | _: DTOId => None
      case al: AliasId => getInitValue(ts.dealias(al))
      case _ => throw new IDLException(s"Impossible getInitValue type: ${id.name}")
    }
  }

  private def getRandomValue(id: TypeId, depth: Int): String = {
    val rnd = new scala.util.Random()
    id match {
      case g: Generic => g match {
        case gm: Generic.TMap => s"new ${CSharpType(gm).renderType(true)}()"
        case gl: Generic.TList => s"new ${CSharpType(gl).renderType(true)}()"
        case gs: Generic.TSet => s"new ${CSharpType(gs).renderType(true)}()"
        case _: Generic.TOption => "null"
      }
      case p: Primitive => p match {
        case Primitive.TBool => rnd.nextBoolean().toString
        case Primitive.TString => "\"str_" + rnd.nextInt(20000) + "\""
        case Primitive.TInt8 => rnd.nextInt(127).toString
        case Primitive.TInt16 => (256 + rnd.nextInt(32767 - 255)).toString
        case Primitive.TInt32 => (32768 + rnd.nextInt(2147483647 - 32767)).toString
        case Primitive.TInt64 => (2147483648L + rnd.nextInt(2147483647)).toString
        case Primitive.TUInt8 => rnd.nextInt(127).toString
        case Primitive.TUInt16 => (256 + rnd.nextInt(32767 - 255)).toString
        case Primitive.TUInt32 => (32768 + rnd.nextInt(2147483647 - 32767)).toString
        case Primitive.TUInt64 => (2147483648L + rnd.nextInt(2147483647)).toString
        case Primitive.TFloat => rnd.nextFloat().toString + "f"
        case Primitive.TDouble => (2147483648L + rnd.nextFloat()).toString
        case Primitive.TBLOB => ???
        case Primitive.TUUID => s"""new System.Guid("${java.util.UUID.randomUUID.toString}")"""
        case Primitive.TTime => s"""System.TimeSpan.Parse(string.Format("{0:D2}:{1:D2}:{2:D2}.{3:D3}", ${rnd.nextInt(24)}, ${rnd.nextInt(60)}, ${rnd.nextInt(60)}, ${100 + rnd.nextInt(100)}))"""
        case Primitive.TDate => s"""System.DateTime.Parse(string.Format("{0:D4}-{1:D2}-{2:D2}", ${1984 + rnd.nextInt(20)}, ${1 + rnd.nextInt(12)}, ${1 + rnd.nextInt(28)}))"""
        case Primitive.TTs => s"""System.DateTime.ParseExact(string.Format("{0:D4}-{1:D2}-{2:D2}T{3:D2}:{4:D2}:{5:D2}.{6:D3}", ${1984 + rnd.nextInt(20)}, ${1 + rnd.nextInt(12)}, ${1 + rnd.nextInt(28)}, ${rnd.nextInt(24)}, ${rnd.nextInt(60)}, ${rnd.nextInt(60)}, ${100 + rnd.nextInt(100)}), JsonNetTimeFormats.Tsl, CultureInfo.InvariantCulture, DateTimeStyles.None)"""
        case Primitive.TTsTz => s"""System.DateTime.ParseExact(string.Format("{0:D4}-{1:D2}-{2:D2}T{3:D2}:{4:D2}:{5:D2}.{6:D3}+10:00", ${1984 + rnd.nextInt(20)}, ${1 + rnd.nextInt(12)}, ${1 + rnd.nextInt(28)}, ${rnd.nextInt(24)}, ${rnd.nextInt(60)}, ${rnd.nextInt(60)}, ${100 + rnd.nextInt(100)}), JsonNetTimeFormats.Tsz, CultureInfo.InvariantCulture, DateTimeStyles.None)"""
        case Primitive.TTsU => s"""System.DateTime.ParseExact(string.Format("{0:D4}-{1:D2}-{2:D2}T{3:D2}:{4:D2}:{5:D2}.{6:D3}Z", ${1984 + rnd.nextInt(20)}, ${1 + rnd.nextInt(12)}, ${1 + rnd.nextInt(28)}, ${rnd.nextInt(24)}, ${rnd.nextInt(60)}, ${rnd.nextInt(60)}, ${100 + rnd.nextInt(100)}), JsonNetTimeFormats.Tsu, CultureInfo.InvariantCulture, DateTimeStyles.None)"""
      }
      case _ => id match {
        case e: EnumId => {
          val enu = ts(e).asInstanceOf[Enumeration]
          s"${e.path.toPackage.map(p => p.capitalize).mkString(".") + "." + e.name}.${enu.members.map(_.value).apply(rnd.nextInt(enu.members.length))}"
        }
        case i: InterfaceId => if (depth <= 0) "null" else randomInterface(i, depth)
        case i: IdentifierId => randomIdentifier(i, depth)
        case a: AdtId => if (depth <= 0) "null" else randomAdt(a, depth)
        case i: DTOId => if (depth <= 0) "null" else randomDto(ts(i).asInstanceOf[DTO], depth)
        case al: AliasId => getRandomValue(ts.dealias(al), depth)
        case _ => throw new IDLException(s"Impossible getRandomValue type: ${id.name}")
      }
    }
  }

  private def randomIdentifier(i: IdentifierId, depth: Int): String = {
    val inst = ts(i).asInstanceOf[Identifier]
    s"""new ${i.path.toPackage.map(p => p.capitalize).mkString(".") + "." + i.name}(
       |${inst.fields.map(f => CSharpType(f.typeId).getRandomValue(depth - 1)).mkString(",\n").shift(4)}
       |)
     """.stripMargin
  }

  private def randomAdt(i: AdtId, depth: Int): String = {
    val adt = ts(i).asInstanceOf[Adt]
    s"""new ${i.path.toPackage.map(p => p.capitalize).mkString(".") + "." + i.name}.${adt.alternatives.head.wireId}(
       |${CSharpType(adt.alternatives.head.typeId).getRandomValue(depth - 1).shift(4)}
       |)
     """.stripMargin
  }

  private def randomInterface(i: InterfaceId, depth: Int): String = {
    val inst = ts(i).asInstanceOf[Interface]
    val structure = ts.structure.structure(inst)
    val eid = ts.tools.implId(inst.id)
    val validFields = structure.all.filterNot(f => inst.struct.superclasses.interfaces.contains(f.defn.definedBy))
    val dto = DTO(eid, Structure(validFields.map(f => f.field), List.empty, Super(List(inst.id), List.empty, List.empty)), NodeMeta.empty)
    randomDto(dto, depth)
  }

  private def randomDto(i: DTO, depth: Int): String = {
      val structure = ts.structure.structure(i)
      val struct = CSharpClass(i.id, i.id.name, structure, List.empty)
      val implIface = ts.inheritance.allParents(i.id).find(ii => ts.tools.implId(ii) == i.id)
      val dtoName = if (implIface.isDefined) implIface.get.path.toPackage.map(p => p.capitalize).mkString(".") + "." + implIface.get.name + i.id.name else
        i.id.path.toPackage.map(p => p.capitalize).mkString(".") + "." + i.id.name
      s"""new ${dtoName}(
         |${struct.fields.map(f => f.tp.getRandomValue(depth - 1)).mkString(",\n").shift(4)}
         |)
       """.stripMargin
  }

    def renderToString(name: String, escape: Boolean): String = {
      val res = id match {
        case Primitive.TString => name
        case Primitive.TInt8 => return s"$name.ToString()"  // No Escaping needed for integers
        case Primitive.TInt16 => return s"$name.ToString()"
        case Primitive.TInt32 => return s"$name.ToString()"
        case Primitive.TInt64 => return s"$name.ToString()"
        case Primitive.TUInt8 => return s"$name.ToString()"
        case Primitive.TUInt16 => return s"$name.ToString()"
        case Primitive.TUInt32 => return s"$name.ToString()"
        case Primitive.TUInt64 => return s"$name.ToString()"
        case Primitive.TBool => return s"$name.ToString()"
        case Primitive.TBLOB => ???
        case Primitive.TUUID => s"$name.ToString()"
        case _: EnumId => s"$name.ToString()"
        case _: IdentifierId => s"$name.ToString()"
        case _ => throw new IDLException(s"Should never render non int, string, or Guid types to strings. Used for type ${id.name}")
      }
      if (escape) {
        s"IRT.Transport.UrlEscaper.Escape($res)"
      } else {
        res
      }
    }

    def renderFromString(src: String, unescape: Boolean, currentDomain: String = ""): String = {
      val source = if (unescape) s"IRT.Transport.UrlEscaper.UnEscape($src)" else src
      id match {
          case Primitive.TString => source
          case Primitive.TInt8 => s"sbyte.Parse($src)"   // No Escaping needed for integers
          case Primitive.TInt16 => s"short.Parse($src)"
          case Primitive.TInt32 => s"int.Parse($src)"
          case Primitive.TInt64 => s"long.Parse($src)"
          case Primitive.TUInt8 => s"byte.Parse($src)"
          case Primitive.TUInt16 => s"ushort.Parse($src)"
          case Primitive.TUInt32 => s"uint.Parse($src)"
          case Primitive.TUInt64 => s"ulong.Parse($src)"
          case Primitive.TBool => s"bool.Parse($src)"
          case Primitive.TUUID => s"new Guid($source)"
          case Primitive.TBLOB => ???
          case _: EnumId => s"${renderType(currentDomain != "" && currentDomain != id.uniqueDomainName)}Helpers.From($source)"
          case i: IdentifierId => s"${i.name}.From($source)"
          case _ => throw new IDLException(s"Should never render non int, string, or Guid types to strings. Used for type ${id.name}")
      }
    }

  def renderType(withPackage: Boolean = false): String = {
    renderNativeType(id, withPackage)
  }

  private def renderNativeType(id: TypeId, withPackage: Boolean): String = id match {
    case g: Generic => renderGenericType(g, withPackage)
    case p: Primitive => renderPrimitiveType(p)
    case _ => renderUserType(id, withPackage = withPackage)
  }

  private def renderGenericType(generic: Generic, withPackage: Boolean): String = {
    generic match {
      case gm: Generic.TMap => s"Dictionary<${renderNativeType(gm.keyType, withPackage)}, ${renderNativeType(gm.valueType, withPackage)}>"
      case gl: Generic.TList => s"List<${renderNativeType(gl.valueType, withPackage)}>"
      case gs: Generic.TSet => s"List<${renderNativeType(gs.valueType, withPackage)}>"
      case go: Generic.TOption => if (!isNullableImpl(go.valueType)) s"Nullable<${renderNativeType(go.valueType, withPackage)}>" else renderNativeType(go.valueType, withPackage)
    }
  }

  protected def renderPrimitiveType(primitive: Primitive): String = primitive match {
    case Primitive.TBool => "bool"
    case Primitive.TString => "string"

    case Primitive.TInt8 => "sbyte"
    case Primitive.TUInt8 => "byte"

    case Primitive.TInt16 => "short"
    case Primitive.TUInt16 => "ushort"

    case Primitive.TInt32 => "int"
    case Primitive.TUInt32 => "uint"

    case Primitive.TInt64 => "long"
    case Primitive.TUInt64 => "ulong"

    case Primitive.TFloat => "float"
    case Primitive.TDouble => "double"
    case Primitive.TUUID => "Guid"
    case Primitive.TBLOB => ???
    case Primitive.TTime => "TimeSpan"
    case Primitive.TDate => "DateTime" // Could be Date
    case Primitive.TTs => "DateTime"
    case Primitive.TTsTz => "DateTime"
    case Primitive.TTsU => "DateTime"
  }

  protected def renderUserType(id: TypeId, withPackage: Boolean = false): String = {
      val fullName = id.path.toPackage.map(p => p.capitalize).mkString(".") + "." + id.name
      id match {
        case _: EnumId => if (withPackage) fullName else s"${im.withImport(id)}"
        case _: InterfaceId => if (withPackage) fullName else s"${im.withImport(id)}"
        case _: IdentifierId => if (withPackage) fullName else s"${im.withImport(id)}"
        case _: AdtId | _: DTOId => if (withPackage) fullName else s"${im.withImport(id)}"
        case al: AliasId => renderNativeType(ts.dealias(al), withPackage)
        case _ => throw new IDLException(s"Impossible renderUserType ${id.name}")
      }
  }

}

object CSharpType {
  def apply(
             id: TypeId
           )(implicit im: CSharpImports , ts: Typespace): CSharpType = new CSharpType(id)
}
