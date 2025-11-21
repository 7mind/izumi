package izumi.logstage.macros

import scala.reflect.macros.blackbox

object EncodingModeExtractors {

  def getModeFromPrefixesEncModeTypeMember(c: blackbox.Context { type PrefixType <: { type EncMode } }): EncodingMode = {
    val prefixTpe = c.prefix.tree.tpe
    val name = prefixTpe.member(c.universe.TypeName("EncMode")).typeSignature.dealias.typeSymbol.name
    getModeFromName(c)(prefixTpe, name.toString)
  }

  def getModeFromType[EncMode: c.WeakTypeTag](c: blackbox.Context): EncodingMode = {
    val encModeTpe = c.weakTypeOf[EncMode]
    val name = encModeTpe.typeSymbol.name
    getModeFromName(c)(encModeTpe, name.toString)
  }

  def getModeFromName(c: blackbox.Context)(debugTpe: c.universe.Type, name: String): EncodingMode = {
    encodingModeStrMap.getOrElse(
      name,
      c.abort(c.enclosingPosition, s"Couldn't get valid type member `EncMode` from $debugTpe, got $name, expected one of ${encodingModeStrMap.keys.mkString("`", ", ", "`")}"),
    )
  }

  private val encodingModeStrMap: Map[String, EncodingMode] = EncodingMode.values.map(k => k.toString -> k).toMap

}
