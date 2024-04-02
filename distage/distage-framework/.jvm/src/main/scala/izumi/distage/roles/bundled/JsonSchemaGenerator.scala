package izumi.distage.roles.bundled

import izumi.distage.config.codec.ConfigMetaType.{TCaseClass, TVariant}
import izumi.distage.config.codec.{ConfigMetaType, ConfigMetaTypeId}
import izumi.distage.config.model.ConfTag
import izumi.distage.roles.bundled.JsonSchemaGenerator.TLAccumulator
import izumi.fundamentals.collections.nonempty.NEList

import scala.collection.mutable

object JsonSchemaGenerator {
  case class TLAccumulator(typings: mutable.HashSet[ConfigMetaType], entries: mutable.HashMap[String, TLAccumulator]) {

    def add(pathElement: String, typing: Option[ConfigMetaType]): Unit = {
      val subAcc = entries.getOrElseUpdate(pathElement, TLAccumulator.emtpy)
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
    def emtpy = new TLAccumulator(mutable.HashSet.empty, mutable.HashMap.empty)

  }
}

class JsonSchemaGenerator(tags: Seq[ConfTag]) {
  def unifyTopLevel(): ConfigMetaType = {
    val types: Seq[NEList[(String, Option[ConfigMetaType])]] = tags.map {
      t =>
        val parts = t.confPath.split('.').toList
        parts.init.map(p => (p, None)) ::: NEList((parts.last, Some(t.tpe)))
    }

    val tl = TLAccumulator.emtpy
    convertIntoTree(types, tl)
    val schema = convertIntoType(Seq.empty, tl)

    println(("TL", schema))
    null
  }

  private def convertIntoType(path: Seq[String], accumulator: TLAccumulator): ConfigMetaType = {
    val hasTypings = accumulator.typings.nonEmpty
    val hasEntries = accumulator.entries.nonEmpty

    val fields = accumulator.entries.toSeq.map {
      case (id, sub) =>
        (id, convertIntoType(path :+ id, sub))
    }

    val id = ConfigMetaTypeId(None, ("_" +: path).mkString("."), Seq.empty)

    val asClass = ConfigMetaType.TCaseClass(id, fields)
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

  private def mergeTypes(typingsSet: Set[ConfigMetaType]) = {
    val classes = typingsSet.collect { case c: TCaseClass => c }
    val ids = typingsSet.map(_.id)
    val allIds = ConfigMetaTypeId(None, s"merged:${ids.mkString(";")}", Seq.empty)

    if (classes.size == typingsSet.size) {
      val fields = classes.flatMap(_.fields).toSeq
      TCaseClass(allIds, fields)
    } else {
      TVariant(allIds, typingsSet)
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
