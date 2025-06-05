package izumi.logstage.macros

import scala.quoted.{Quotes, Type}

object EncodingModeExtractors {

  def getModeFromType[EncMode: Type](using qctx: Quotes): EncodingMode = {
    import qctx.reflect.*
    val encModeTpe = TypeRepr.of[EncMode].dealias
    val name = encModeTpe.typeSymbol.name
    getModeFromName(encModeTpe, name)
  }

  def getModeFromName(using qctx: Quotes)(debugTpe: qctx.reflect.TypeRepr, name: String): EncodingMode = {
    encodingModeStrMap.getOrElse(
      name,
      qctx.reflect.report.errorAndAbort(
        s"Couldn't get valid type member `EncMode` from ${debugTpe.show}, got $name, expected one of ${encodingModeStrMap.keys.mkString("`", ", ", "`")}"
      ),
    )
  }

  private val encodingModeStrMap: Map[String, EncodingMode] = EncodingMode.values.map(k => s"${k.toString}$$" -> k).toMap

}
