package izumi.distage.roles.bundled

import io.circe.{Json, JsonObject}
import izumi.distage.config.codec.ConfigMetaType.{ConfigField, TCaseClass, TVariant}
import izumi.distage.config.codec.{ConfigMetaBasicType, ConfigMetaType, ConfigMetaTypeId}
import izumi.distage.config.model.ConfTag
import izumi.distage.roles.bundled.JsonSchemaGenerator.TLAccumulator
import izumi.fundamentals.collections.nonempty.NEList

import scala.collection.mutable

class JsonSchemaGenerator {

  def generateSchema(tags: Seq[ConfTag]): Json = {
    val unified = unifyTopLevel(tags)
    val schema = generateSchema(unified)
    schema
  }

  private def unifyTopLevel(tags: Seq[ConfTag]): ConfigMetaType = {
    val types: Seq[NEList[(String, Option[ConfigMetaType])]] = tags.map {
      t =>
        val parts = t.confPath.split('.').toList
        parts.init.map(p => (p, None)) ::: NEList((parts.last, Some(t.tpe)))
    }

    val tl = TLAccumulator.empty
    convertIntoTree(types, tl)
    convertIntoType(Seq.empty, tl)
  }

  private def generateSchema(meta: ConfigMetaType): Json = {
    val index = mutable.HashMap.empty[String, Json]
    generateSchema(meta, index)

    index.get(meta.id.toString).flatMap(_.asObject) match {
      case Some(value) =>
        value.add("$defs", JsonObject(index.toSeq*).toJson).toJson
      case _ =>
        JsonObject("$comment" -> Json.fromString(s"Failed to generate schema for $meta, please report as a bug")).toJson
    }
  }

  private def genDoc(doc: Option[String]): Option[(String, Json)] = {
    doc.map(doc => "$comment" -> Json.fromString(doc))
  }

  private def generateSchema(meta: ConfigMetaType, defs: mutable.Map[String, Json]): Unit = {
    val id = meta.id.toString

    val schema = meta match {
      case cc: TCaseClass =>
        val props = JsonObject(cc.fields.map { case ConfigField(n, t, doc) => (n, refOf(t.id).deepMerge(JsonObject(genDoc(doc).toSeq*)).toJson) }*).toJson
        cc.fields.foreach {
          case ConfigField(_, tpe, _) =>
            generateSchema(tpe, defs)
        }
        val optional = cc.fields.collect {
          case ConfigField(n, ConfigMetaType.TOption(_), _) =>
            n
        }.toSet
        val required = cc.fields.map(_.name).toSet.diff(optional)
        val fields = Seq("type" -> Json.fromString("object"), "properties" -> props, "required" -> Json.fromValues(required.map(Json.fromString))) ++ genDoc(cc.doc)
        JsonObject(fields*).toJson

      case st: ConfigMetaType.TSealedTrait =>
        st.branches.foreach {
          case (_, tpe) => generateSchema(tpe, defs)
        }
        val fields = Seq("anyOf" -> Json.fromValues(st.branches.map(_._2.id).map(refOf).map(_.toJson))) ++ genDoc(st.doc)
        JsonObject(fields*).toJson

      case _: ConfigMetaType.TUnknown =>
        JsonObject().toJson

      case ConfigMetaType.TBasic(tpe) =>
        tpe match {
          case ConfigMetaBasicType.TString => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TChar => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TBoolean => JsonObject("type" -> Json.fromString("boolean")).toJson
          case ConfigMetaBasicType.TDouble => JsonObject("type" -> Json.fromString("number")).toJson
          case ConfigMetaBasicType.TFloat => JsonObject("type" -> Json.fromString("number")).toJson
          case ConfigMetaBasicType.TInt => JsonObject("type" -> Json.fromString("integer")).toJson
          case ConfigMetaBasicType.TLong => JsonObject("type" -> Json.fromString("integer")).toJson
          case ConfigMetaBasicType.TShort => JsonObject("type" -> Json.fromString("integer")).toJson
          case ConfigMetaBasicType.TByte => JsonObject("type" -> Json.fromString("integer")).toJson
          case ConfigMetaBasicType.TURL => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TUUID => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TPath => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TURI => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TPattern => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TRegex => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TInstant => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TZoneOffset => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TZoneId => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TPeriod => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TChronoUnit => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TJavaDuration => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TYear => JsonObject("type" -> Json.fromString("integer")).toJson
          case ConfigMetaBasicType.TDuration => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TFiniteDuration => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TBigInteger => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TBigInt => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TBigDecimal => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TJavaBigDecimal => JsonObject("type" -> Json.fromString("string")).toJson
          case ConfigMetaBasicType.TConfig => JsonObject().toJson
          case ConfigMetaBasicType.TConfigObject => JsonObject("type" -> Json.fromString("object")).toJson
          case ConfigMetaBasicType.TConfigValue => JsonObject().toJson
          case ConfigMetaBasicType.TConfigList => JsonObject("type" -> Json.fromString("array")).toJson
          case ConfigMetaBasicType.TConfigMemorySize => JsonObject("type" -> Json.fromString("string")).toJson
        }
      case ConfigMetaType.TList(tpe) =>
        generateSchema(tpe, defs)
        JsonObject("type" -> Json.fromString("array"), "items" -> refOf(tpe.id).toJson).toJson

      case ConfigMetaType.TSet(tpe) =>
        generateSchema(tpe, defs)
        JsonObject("type" -> Json.fromString("array"), "items" -> refOf(tpe.id).toJson).toJson

      case ConfigMetaType.TOption(tpe) =>
        generateSchema(tpe)
        refOf(tpe.id).toJson

      case m: ConfigMetaType.TMap =>
        JsonObject("$comment" -> Json.fromString(s"typed map type ${m.id} cannot be encoded with json schema")).toJson
      case v: TVariant =>
        JsonObject("$comment" -> Json.fromString(s"variant type ${v.id} cannot be encoded with json schema")).toJson
    }

    defs.update(id, schema)
  }

  private def refOf(id: ConfigMetaTypeId): JsonObject = {
    JsonObject("$ref" -> Json.fromString(s"#/$$defs/$id"))
  }

  private def convertIntoType(path: Seq[String], accumulator: TLAccumulator): ConfigMetaType = {
    val hasTypings = accumulator.typings.nonEmpty
    val hasEntries = accumulator.entries.nonEmpty

    val fields = accumulator.entries.toSeq.map {
      case (id, sub) =>
        val converted = convertIntoType(path :+ id, sub)
        ConfigField(id, converted, None)
    }

    val id = ConfigMetaTypeId(None, ("_" +: path).mkString("."), Seq.empty)

    val asClass = ConfigMetaType.TCaseClass(id, fields, None)
    val typingsSet = accumulator.typings.toSet

    if (hasEntries && !hasTypings) {
      asClass
    } else if (!hasEntries && hasTypings) {
      if (accumulator.typings.size == 1) {
        accumulator.typings.head
      } else {
        assert(accumulator.typings.size > 1)
        mergeTypes(typingsSet)
      }
    } else if (hasEntries && hasTypings) {
      mergeTypes(typingsSet + asClass)
    } else {
      assert(!hasEntries && !hasTypings)
      ConfigMetaType.TUnknown()
    }

  }

  private def mergeTypes(typingsSet: Set[ConfigMetaType]): ConfigMetaType = {
    val classes = typingsSet.collect { case c: TCaseClass => c }
    val ids = typingsSet.map(_.id)
    val allIds = ConfigMetaTypeId(None, s"merged:${ids.mkString(";")}", Seq.empty)

    val maybeDoc = typingsSet.flatMap(_.doc)
    val doc = Option(maybeDoc).filterNot(_.isEmpty)

    if (classes.size == typingsSet.size) {
      val fields = classes.flatMap(_.fields).toSeq
      val fullDoc = doc.map(docs => (Seq(s"Merged case class (${typingsSet.map(_.id.toString).mkString(", ")})") ++ docs).mkString("\n"))
      TCaseClass(allIds, fields, fullDoc)
    } else {
      val fullDoc = doc.map(docs => (Seq(s"Variant class merged from (${typingsSet.map(_.id.toString).mkString(", ")})") ++ docs).mkString("\n"))
      TVariant(allIds, typingsSet, fullDoc)
    }
  }

  private def convertIntoTree(types: Seq[NEList[(String, Option[ConfigMetaType])]], level: TLAccumulator): Unit = {
    val tails = types
      .map {
        path =>
          val (pathElement, tpe) = path.head
          level.add(pathElement, tpe)
          (pathElement, NEList.from(path.tail))
      }.groupBy(_._1).map {
        case (k, v) =>
          (k, v.flatMap(_._2))
      }

    tails.foreach {
      case (subName, paths) =>
        val sub = level.get(subName)
        convertIntoTree(paths, sub)
    }
  }
}

object JsonSchemaGenerator {
  final case class TLAccumulator(typings: mutable.HashSet[ConfigMetaType], entries: mutable.HashMap[String, TLAccumulator]) {

    def add(pathElement: String, typing: Option[ConfigMetaType]): Unit = {
      val subAcc = entries.getOrElseUpdate(pathElement, TLAccumulator.empty)
      typing.foreach {
        tpe =>
          subAcc.typings.add(tpe)
      }
    }

    def get(name: String): TLAccumulator = {
      entries(name)
    }
  }
  object TLAccumulator {
    def empty = new TLAccumulator(mutable.HashSet.empty, mutable.HashMap.empty)
  }
}
