package izumi.idealingua.translator.tocsharp.extensions

import izumi.fundamentals.platform.language.Quirks.discard
import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import izumi.idealingua.model.problems.IDLException
import izumi.idealingua.model.il.ast.typed.DefMethod
import izumi.idealingua.model.il.ast.typed.DefMethod.Output.{Alternative, Singular}
import izumi.idealingua.model.il.ast.typed.TypeDef._
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.tocsharp.types.{CSharpClass, CSharpField, CSharpType}
import izumi.idealingua.translator.tocsharp.{CSTContext, CSharpImports}

object JsonNetExtension extends CSharpTranslatorExtension {
  override def preModelEmit(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    s"[JsonConverter(typeof(${id.id.name}_JsonNetConverter))]"
  }

  override def postModelEmit(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    s"""public class ${id.id.name}_JsonNetConverter: JsonNetConverter<${id.id.name}> {
       |    public override void WriteJson(JsonWriter writer, ${id.id.name} value, JsonSerializer serializer) {
       |        writer.WriteValue(value.ToString());
       |    }
       |
       |    public override ${id.id.name} ReadJson(JsonReader reader, System.Type objectType, ${id.id.name} existingValue, bool hasExistingValue, JsonSerializer serializer) {
       |        return ${id.id.name}.From((string)reader.Value);
       |    }
       |}
     """.stripMargin
  }

  override def imports(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): List[String] = {
    discard(ctx)
    List("Newtonsoft.Json", "IRT.Marshaller")
  }

  override def preModelEmit(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    s"[JsonConverter(typeof(${id.id.name}_JsonNetConverter))]"
  }

  override def postModelEmit(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    s"""public class ${id.id.name}_JsonNetConverter: JsonNetConverter<${id.id.name}> {
       |    public override void WriteJson(JsonWriter writer, ${id.id.name} value, JsonSerializer serializer) {
       |        writer.WriteValue(value.ToString());
       |    }
       |
       |    public override ${id.id.name} ReadJson(JsonReader reader, System.Type objectType, ${id.id.name} existingValue, bool hasExistingValue, JsonSerializer serializer) {
       |        return ${id.id.name}Helpers.From((string)reader.Value);
       |    }
       |}
     """.stripMargin
  }

  override def imports(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): List[String] = {
    discard(ctx)
    List("Newtonsoft.Json", "IRT.Marshaller")
  }

  override def preModelEmit(ctx: CSTContext, name: String, struct: CSharpClass)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    s"[JsonConverter(typeof(${name}_JsonNetConverter))]"
  }

  override def preModelEmit(ctx: CSTContext, i: DTO)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    val implIface = ts.inheritance.allParents(i.id).find(ii => ts.tools.implId(ii) == i.id)
    val converterName = if (implIface.isDefined) implIface.get.name + i.id.name else i.id.name

    s"[JsonConverter(typeof(${converterName}_JsonNetConverter))]"
  }

  override def postModelEmit(ctx: CSTContext, i: DTO)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    val structure = ts.structure.structure(i)
    val struct = CSharpClass(i.id, i.id.name, structure, List.empty)
    val implIface = ts.inheritance.allParents(i.id).find(ii => ts.tools.implId(ii) == i.id)
    val converterName = if (implIface.isDefined) implIface.get.name + i.id.name else i.id.name

    this.postModelEmit(ctx, converterName, struct)
  }

  override def postModelEmit(ctx: CSTContext, name: String, struct: CSharpClass)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    // ${if (struct.fields.isEmpty) "" else "var json = JObject.Load(reader);"}
    val currentDomain = struct.id.uniqueDomainName

    s"""public class ${name}_JsonNetConverter: JsonNetConverter<$name> {
       |    public override void WriteJson(JsonWriter writer, $name v, JsonSerializer serializer) {
       |        writer.WriteStartObject();
       |${struct.fields.map(f => writeProperty(f)).mkString("\n").shift(8)}
       |        writer.WriteEndObject();
       |    }
       |
       |    public override $name ReadJson(JsonReader reader, System.Type objectType, $name existingValue, bool hasExistingValue, JsonSerializer serializer) {
       |        ${if (struct.fields.isEmpty) "reader.Skip();" else "var json = JObject.Load(reader);"}
       |${struct.fields.map(f => prepareReadProperty(f, currentDomain)).filter(_.isDefined).map(_.get).mkString("\n").shift(8)}
       |        return new $name(
       |${struct.fields.map(f => readProperty(f, currentDomain)).mkString(", \n").shift(12)}
       |        );
       |    }
       |}
     """.stripMargin
  }

  override def imports(ctx: CSTContext, id: DTO)(implicit im: CSharpImports, ts: Typespace): List[String] = {
    discard(ctx)
    List("Newtonsoft.Json", "Newtonsoft.Json.Linq", "IRT.Marshaller")
  }

  private def writePropertyValue(src: String, t: CSharpType, key: Option[String] = None, depth: Int = 1)(implicit im: CSharpImports, ts: Typespace): String = {
    t.id match {
      case g: Generic.TOption =>
        val optionType = CSharpType(g.valueType)
        s"""if (${if (optionType.isNullable) src + " != null" else src + ".HasValue"}) {
           |${writePropertyValue(if (optionType.isNullable) src else src + ".Value", optionType, key).shift(4)}
           |}
         """.stripMargin
      case al: AliasId => writePropertyValue(src, CSharpType(ts.dealias(al)), key)
      case _ =>
        (if (key.isDefined) s"""writer.WritePropertyName("${key.get}");\n""" else "") + (
          t.id match {
            case g: Generic => g match {
              case m: Generic.TMap =>
                val iter = s"mkv${if (depth > 1) depth.toString else ""}"
                s"""writer.WriteStartObject();
                   |foreach(var $iter in $src) {
                   |    writer.WritePropertyName($iter.Key.ToString());
                   |${writePropertyValue(s"$iter.Value", CSharpType(m.valueType), depth = depth + 1).shift(4)}
                   |}
                   |writer.WriteEndObject();
                 """.stripMargin
              case l: Generic.TList =>
                val iter = s"lv${if (depth > 1) depth.toString else ""}"
                s"""writer.WriteStartArray();
                   |foreach (var $iter in $src) {
                   |${writePropertyValue(s"$iter", CSharpType(l.valueType), depth = depth + 1).shift(4)}
                   |}
                   |writer.WriteEndArray();
                 """.stripMargin
              case s: Generic.TSet =>
                val iter = s"lv${if (depth > 1) depth.toString else ""}"
                s"""writer.WriteStartArray();
                   |foreach (var $iter in $src) {
                   |${writePropertyValue(s"$iter", CSharpType(s.valueType), depth = depth + 1).shift(4)}
                   |}
                   |writer.WriteEndArray();
                 """.stripMargin
              case _ => throw new Exception("Option should have been checked already.")
            }
            case p: Primitive => p match {
              case Primitive.TBool => s"writer.WriteValue($src);"
              case Primitive.TString => s"writer.WriteValue($src);"
              case Primitive.TInt8 => s"writer.WriteValue($src);"
              case Primitive.TInt16 => s"writer.WriteValue($src);"
              case Primitive.TInt32 => s"writer.WriteValue($src);"
              case Primitive.TInt64 => s"writer.WriteValue($src);"
              case Primitive.TUInt8 => s"writer.WriteValue($src);"
              case Primitive.TUInt16 => s"writer.WriteValue($src);"
              case Primitive.TUInt32 => s"writer.WriteValue($src);"
              case Primitive.TUInt64 => s"writer.WriteValue($src);"
              case Primitive.TFloat => s"writer.WriteValue($src);"
              case Primitive.TDouble => s"writer.WriteValue($src);"
              case Primitive.TBLOB => ???
              case Primitive.TUUID => s"writer.WriteValue($src.ToString());"
              case Primitive.TTime => s"""writer.WriteValue(string.Format("{0:00}:{1:00}:{2:00}.{3:000}", (int)$src.TotalHours, $src.Minutes, $src.Seconds, $src.Milliseconds));"""
              case Primitive.TDate => s"""writer.WriteValue($src.ToString("yyyy-MM-dd"));"""
              case Primitive.TTs => s"""writer.WriteValue($src.ToString(JsonNetTimeFormats.TslDefault, CultureInfo.InvariantCulture));"""
              case Primitive.TTsTz => s"""writer.WriteValue($src.ToString($src.Kind == DateTimeKind.Utc ? JsonNetTimeFormats.TsuDefault : JsonNetTimeFormats.TszDefault, CultureInfo.InvariantCulture));"""
              case Primitive.TTsU => s"""writer.WriteValue($src.ToUniversalTime().ToString(JsonNetTimeFormats.TsuDefault, CultureInfo.InvariantCulture));"""
            }

            case _ => t.id match {
              case _: EnumId | _: IdentifierId => s"""writer.WriteValue($src.ToString());"""
              case _: InterfaceId => renderSerialize(t.id, src)
              case _: AdtId | _: DTOId => s"""serializer.Serialize(writer, $src);"""
              case _ => throw new IDLException(s"Impossible writePropertyValue type: ${t.id}")
            }
          }
          )
    }
  }

  private def writeProperty(f: CSharpField)(implicit im: CSharpImports, ts: Typespace): String = {
    writePropertyValue("v." + f.renderMemberName(), f.tp, Some(f.name))
  }

  private def propertyNeedsPrepare(i: TypeId)(implicit im: CSharpImports, ts: Typespace): Boolean = i match {
    case g: Generic => g match {
      case _: Generic.TMap => true
      case _: Generic.TList => true
      case _: Generic.TSet => true
      case _: Generic.TOption => true
    }
    case p: Primitive => p match {
      case Primitive.TBool => false
      case Primitive.TString => false
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
    case c => c match {
      case _: EnumId | _: IdentifierId => false
      case _: DTOId => true
      case _: InterfaceId | _: AdtId => false
      case al: AliasId => propertyNeedsPrepare(ts.dealias(al))
      case _ => throw new IDLException(s"Impossible propertyNeedsPrepare type: $i")
    }
  }

  private def prepareReadProperty(f: CSharpField, currentDomain: String)(implicit im: CSharpImports, ts: Typespace): Option[String] = {
    if (!propertyNeedsPrepare(f.tp.id)) {
      None
    } else {
      prepareReadPropertyValue(s"""json["${f.name}"]""", s"_${f.name}", f.tp, createDst = true, currentDomain)
    }
  }

  private def prepareReadPropertyValue(src: String, dst: String, i: CSharpType, createDst: Boolean, currentDomain: String)(implicit im: CSharpImports, ts: Typespace): Option[String] = {
    if (!propertyNeedsPrepare(i.id)) {
      None
    } else {
      i.id match {
        case gm: Generic.TMap =>
          val mk = CSharpType(gm.keyType)
          val mt = CSharpType(gm.valueType)
          Some(
            s"""${if (createDst) "var " else " "}$dst = new ${i.renderType(true)}();
               |foreach (var ${dst}_kv in ((JObject)$src).Properties()) {
               |    ${mt.renderType(true)} ${dst}_dv;
               |${(if (propertyNeedsPrepare(mt.id)) prepareReadPropertyValue(dst + "_kv.Value", dst + "_dv", mt, createDst = false, currentDomain).get else s"${dst}_dv = ${readPropertyValue(dst + "_kv.Value", mt, currentDomain)};").shift(4)}
               |    $dst.Add(${mk.renderFromString(dst + "_kv.Name", unescape = false, currentDomain)}, ${dst}_dv);
               |}
             """.stripMargin
          )

        case gl: Generic.TList =>
          val lt = CSharpType(gl.valueType)
          Some(
            s"""${if (createDst) "var " else " "}$dst = new ${i.renderType(true)}();
               |foreach (var ${dst}_sv in (JArray)$src) {
               |    ${lt.renderType(true)} ${dst}_d;
               |${(if (propertyNeedsPrepare(gl.valueType)) prepareReadPropertyValue(dst + "_sv", dst + "_d", lt, createDst = false, currentDomain).get else s"${dst}_d = ${readPropertyValue(dst + "_sv", lt, currentDomain)};").shift(4)}
               |    $dst.Add(${dst}_d);
               |}
             """.stripMargin
          )

        case gs: Generic.TSet =>
          val st = CSharpType(gs.valueType)
          Some(
            s"""${if (createDst) "var " else " "}$dst = new ${i.renderType(true)}();
               |foreach (var ${dst}_lv in (JArray)$src) {
               |    ${st.renderType(true)} ${dst}_d;
               |${(if (propertyNeedsPrepare(gs.valueType)) prepareReadPropertyValue(dst + "_lv", dst + "_d", st, createDst = false, currentDomain).get else s"${dst}_d = ${readPropertyValue(dst + "_lv", st, currentDomain)};").shift(4)}
               |    $dst.Add(${dst}_d);
               |}
             """.stripMargin
          )

        case o: Generic.TOption =>
          val ot = CSharpType(o.valueType)
          Some(
            s"""${i.renderType(true)} $dst = null;
               |if ($src != null && $src.Type != JTokenType.Null) {
               |${(if (propertyNeedsPrepare(o.valueType)) prepareReadPropertyValue(src, dst, ot, createDst = false, currentDomain).get else s"$dst = ${readPropertyValue(src, ot, currentDomain)};").shift(4)}
               |}
             """.stripMargin)

        case al: AliasId =>
          prepareReadPropertyValue(src, dst, CSharpType(ts.dealias(al)), createDst = createDst, currentDomain)

        case _: DTOId =>
//          Some(
//            s"""${if (createDst) "var " else " "}$dst = new ${i.renderType(true)}();
//               |$dst = serializer.Deserialize<${i.renderType(true)}>($src.CreateReader());""".stripMargin
//          )
          Some(
            s"""${if (createDst) "var " else ""}$dst = serializer.Deserialize<${i.renderType(true)}>($src.CreateReader());""".stripMargin
          )

        case _ => throw new Exception("Other cases should have been checked already.")
      }
    }
  }

  private def readPropertyValue(src: String, t: CSharpType, currentDomain: String)(implicit im: CSharpImports, ts: Typespace): String = {
    t.id match {
      case p: Primitive => p match {
        case Primitive.TBool => s"$src.Value<bool>()"
        case Primitive.TString => s"$src.Value<string>()"
        case Primitive.TInt8 => s"$src.Value<sbyte>()"
        case Primitive.TInt16 => s"$src.Value<short>()"
        case Primitive.TInt32 => s"$src.Value<int>()"
        case Primitive.TInt64 => s"$src.Value<long>()"
        case Primitive.TUInt8 => s"$src.Value<byte>()"
        case Primitive.TUInt16 => s"$src.Value<ushort>()"
        case Primitive.TUInt32 => s"$src.Value<uint>()"
        case Primitive.TUInt64 => s"$src.Value<ulong>()"
        case Primitive.TFloat => s"$src.Value<float>()"
        case Primitive.TDouble => s"$src.Value<double>()"
        case Primitive.TBLOB => ???
        case Primitive.TUUID => s"new System.Guid($src.Value<string>())"
        case Primitive.TTime => s"TimeSpan.Parse($src.Value<string>())"
        case Primitive.TDate => s"DateTime.Parse($src.Value<string>())"
        case Primitive.TTs => s"DateTime.ParseExact($src.Value<string>(), JsonNetTimeFormats.Tsl, CultureInfo.InvariantCulture, DateTimeStyles.None)"
        case Primitive.TTsTz => s"DateTime.ParseExact($src.Value<string>(), JsonNetTimeFormats.Tsz, CultureInfo.InvariantCulture, DateTimeStyles.None)"
        case Primitive.TTsU => s"DateTime.ParseExact($src.Value<string>(), JsonNetTimeFormats.Tsu, CultureInfo.InvariantCulture, DateTimeStyles.None)"
      }
      case _ => t.id match {
        case _: EnumId => s"${t.renderType(t.id.uniqueDomainName != currentDomain)}Helpers.From($src.Value<string>())"
        case _: IdentifierId => s"${t.renderType(true)}.From($src.Value<string>())"
        case _: InterfaceId | _: AdtId => s"serializer.Deserialize<${t.renderType(true)}>($src.CreateReader())"
        case al: AliasId => readPropertyValue(src, CSharpType(ts.dealias(al)), currentDomain)
        case _ => throw new IDLException(s"Impossible readPropertyValue type: ${t.id}")
      }
    }
  }

  private def readProperty(f: CSharpField, currentDomain: String)(implicit im: CSharpImports, ts: Typespace): String = {
    if (propertyNeedsPrepare(f.tp.id))
      s"_${f.name}"
    else
      readPropertyValue(s"""json["${f.name}"]""", f.tp, currentDomain)
  }

  override def preModelEmit(ctx: CSTContext, id: Interface)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    s"[JsonConverter(typeof(${id.id.name}_JsonNetConverter))]"
  }

  override def postModelEmit(ctx: CSTContext, id: Interface)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    val eid = ts.tools.implId(id.id)
    val eidName = id.id.name + eid.name
    s"""public class ${id.id.name}_JsonNetConverter: JsonNetConverter<${id.id.name}> {
       |    public override void WriteJson(JsonWriter writer, ${id.id.name} value, JsonSerializer serializer) {
       |${renderSerialize(id.id, "value").shift(8)}
       |    }
       |
       |    public override ${id.id.name} ReadJson(JsonReader reader, System.Type objectType, ${id.id.name} existingValue, bool hasExistingValue, JsonSerializer serializer) {
       |        var json = JObject.Load(reader);
       |        var kv = json.Properties().First();
       |        var res = $eidName.CreateInstance(kv.Name);
       |        serializer.Populate(kv.Value.CreateReader(), res);
       |        return (${id.id.name})res;
       |    }
       |}
     """.stripMargin
  }

  override def imports(ctx: CSTContext, id: Interface)(implicit im: CSharpImports, ts: Typespace): List[String] = {
    discard(ctx)
    List("Newtonsoft.Json", "System.Linq", "Newtonsoft.Json.Linq", "IRT.Marshaller")
  }

  override def preModelEmit(ctx: CSTContext, i: Adt)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    s"[JsonConverter(typeof(${i.id.name}_JsonNetConverter))]"
  }

  private def renderSerialize(id: TypeId, varName: String): String = {
    id match {
      case _: InterfaceId =>
        s"""// Serializing polymorphic type ${id.name}
           |writer.WriteStartObject();
           |writer.WritePropertyName($varName.GetFullClassName());
           |serializer.Serialize(writer, $varName);
           |writer.WriteEndObject();
        """.stripMargin


      case _ => s"""serializer.Serialize(writer, $varName);"""
    }
  }

  override def postModelEmit(ctx: CSTContext, i: Adt)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)

    s"""public class ${i.id.name}_JsonNetConverter: JsonNetConverter<${i.id.name}> {
       |    public override void WriteJson(JsonWriter writer, ${i.id.name} al, JsonSerializer serializer) {
       |        writer.WriteStartObject();
       |${
      i.alternatives.map(m =>
        s"""if (al is ${i.id.name}.${m.wireId}) {
           |    writer.WritePropertyName("${m.wireId}");
           |    var v = (al as ${i.id.name}.${m.wireId}).Value;
           |${renderSerialize(m.typeId, "v").shift(4)}
           |} else""".stripMargin).mkString("\n").shift(8)
    }
       |        {
       |            throw new System.Exception("Unknown ${i.id.name} type: " + al);
       |        }
       |        writer.WriteEndObject();
       |    }
       |
       |    public override ${i.id.name} ReadJson(JsonReader reader, System.Type objectType, ${i.id.name} existingValue, bool hasExistingValue, JsonSerializer serializer) {
       |        var json = JObject.Load(reader);
       |        var kv = json.Properties().First();
       |        switch (kv.Name) {
       |${
      i.alternatives.map(m =>
        s"""case "${m.wireId}": {
           |    var v = serializer.Deserialize<${CSharpType(m.typeId).renderType(true)}>(kv.Value.CreateReader());
           |    return new ${i.id.name}.${m.wireId}(v);
           |}
           """.stripMargin).mkString("\n").shift(12)
    }
       |            default:
       |                throw new System.Exception("Unknown ${i.id.name} type: " + kv.Name);
       |        }
       |    }
       |}
     """.stripMargin
  } // ${alternatives.map(a => "case '" + (if (a.memberName.isEmpty) a.typeId.name else a.memberName.get)

  override def imports(ctx: CSTContext, id: Adt)(implicit im: CSharpImports, ts: Typespace): List[String] = {
    discard(ctx)
    List("Newtonsoft.Json", "System.Linq", "Newtonsoft.Json.Linq", "IRT.Marshaller")
  }

  override def preModelEmit(ctx: CSTContext, name: String, alternative: Alternative)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)
    s"[JsonConverter(typeof(${name}_JsonNetConverter))]"
  }

  private def renderSerializeOutput(output: DefMethod.Output, varName: String): String = output match {
    case si: Singular => si.typeId match {
      case inf: InterfaceId =>
        s"""// Serializing polymorphic type ${inf.name}
           |writer.WriteStartObject();
           |writer.WritePropertyName($varName.GetFullClassName());
           |serializer.Serialize(writer, $varName);
           |writer.WriteEndObject();
        """.stripMargin

      case _ => s"""serializer.Serialize(writer, $varName);"""
    }
    case _ => s"""serializer.Serialize(writer, $varName);"""
  }

  override def postModelEmit(ctx: CSTContext, name: String, alternative: Alternative, leftType: TypeId, rightType: TypeId)(implicit im: CSharpImports, ts: Typespace): String = {
    discard(ctx)

    val left = CSharpType(leftType).renderType(true)
    val right = CSharpType(rightType).renderType(true)
    s"""public class ${name}_JsonNetConverter: JsonNetConverter<$name> {
       |    public override void WriteJson(JsonWriter writer, $name al, JsonSerializer serializer) {
       |        writer.WriteStartObject();
       |
       |        if (al.IsLeft()) {
       |            writer.WritePropertyName("Failure");
       |            var l = al.GetLeft();
       |${renderSerializeOutput(alternative.failure, "l").shift(12)}
       |        } else {
       |            writer.WritePropertyName("Success");
       |            var r = al.GetRight();
       |${renderSerializeOutput(alternative.success, "r").shift(12)}
       |        }
       |
       |        writer.WriteEndObject();
       |    }
       |
       |    public override $name ReadJson(JsonReader reader, System.Type objectType, $name existingValue, bool hasExistingValue, JsonSerializer serializer) {
       |        var json = JObject.Load(reader);
       |        var kv = json.Properties().First();
       |        switch (kv.Name) {
       |            case "Success": {
       |                var v = serializer.Deserialize<$right>(kv.Value.CreateReader());
       |                return new Either<$left, $right>.Right(v);
       |            }
       |
       |            case "Failure": {
       |                var v = serializer.Deserialize<$left>(kv.Value.CreateReader());
       |                return new Either<$left, $right>.Left(v);
       |            }
       |
       |            default:
       |                throw new System.Exception("Unknown either $name type: " + kv.Name);
       |        }
       |    }
       |}
     """.stripMargin
  }
}
