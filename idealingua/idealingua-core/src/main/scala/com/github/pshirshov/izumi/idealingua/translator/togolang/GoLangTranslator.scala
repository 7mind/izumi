package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common.{Indefinite, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.EphemeralId
import com.github.pshirshov.izumi.idealingua.model.il.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il.{ILAst, Typespace}
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}

import scala.collection.mutable
import GoLangTypeConverter._

class GoLangTranslator(typespace: Typespace) {

  val padding: String = " " * 4

  protected val packageObjects: mutable.HashMap[ModuleId, mutable.ArrayBuffer[String]] = {
    mutable.HashMap[ModuleId, mutable.ArrayBuffer[String]]()
  }

  def translate(): Seq[Module] = {
    typespace.domain
      .types
      .flatMap(translateType) ++
      packageObjects.map {
        case (id, content) =>
          val code =
            s"""
               |${content.map(_.toString()).mkString("\n")}
           """.stripMargin
          Module(id, withPackage(id.path.init, code))
      }
  }

  protected def translateType(definition: ILAst): Seq[Module] = {
    val defns = definition match {
      case a: Alias =>
        packageObjects.getOrElseUpdate(toModuleId(a), mutable.ArrayBuffer()) ++= renderAlias(a)
        Seq()
      case i: Enumeration =>
        renderEnumeration(i)
      case i: Identifier =>
        renderIdentifier(i)
      case i: Interface =>
        renderInterface(i)
      case d: DTO =>
        renderComposite(d.id)
      case d: Adt =>
        renderAdt(d)
      case _ => Seq.empty
    }

    if (defns.nonEmpty) {
      toSource(Indefinite(definition.id), toModuleId(definition), defns)
    } else {
      Seq.empty
    }
  }

  protected def renderAlias(i: Alias): Seq[String] = {
    Seq(s"type ${i.id.name} = ${toGoLang(i.target).render()}")
  }

  def renderEnumeration(i: Enumeration): Seq[String] = {
    val name = i.id.name

    val values = (i.members match {
      case first :: rest => s"$first $name = iota" +: rest
    }).map(v => padding + v).mkString("\n")

    Seq(s"""
       |type $name int8
       |
       |const (
       |$values
       |)
     """.stripMargin
    )
  }

  protected def renderIdentifier(i: Identifier): Seq[String] = {
    renderStruct(i.id.name, typespace.enumFields(i).toGoLangFields.all)
  }

  private def renderStruct(name: String, fields: List[GoLangField]) = {
    val fieldsStr = fields.map { f =>
      padding + f.name + " " + f.fieldType.render()
    }.mkString("\n")

    Seq(
      s"""
         |type $name struct {
         |$fieldsStr
         |}""".stripMargin
    )
  }

  protected def renderInterface(i: Interface): Seq[String] = {
    val fields = typespace.enumFields(i)

    val methods = fields.toGoLangFields.all.map {
      f =>
        s"$padding${f.name.capitalize}() ${f.fieldType.render()} "
    }.mkString("\n")

    val implType = EphemeralId(i.id, typespace.toDtoName(i.id))

    Seq(
      s"""type ${i.id.name} interface {
         |$methods
         |}""".stripMargin
    ) ++ renderComposite(implType)
  }

  private def renderComposite(id: TypeId): Seq[String] = {
    val implTypeName = id.name
    val interfaces = typespace.getComposite(id)
    val fields = typespace.enumFields(interfaces)

    val goLangFields = fields.toGoLangFields.all

    val structImpl = renderStruct(implTypeName, goLangFields)

    val constructorArgs = goLangFields.map { f =>
      s"${f.name.toLowerCase} ${f.fieldType.render()}"
    }.mkString(", ")

    val constructorFieldsInit = goLangFields.map { f =>
      s"$padding${f.name}: ${f.name.toLowerCase},"
    }.mkString("\n")

    val constuctorBody =
      s"""return &$implTypeName {
         |$constructorFieldsInit
         |}""".stripMargin.split("\n").map(padding + _).mkString("\n")

    val constructor =
      s"""func New${id.name}($constructorArgs) $implTypeName {
         |$constuctorBody
         |}""".stripMargin

    val implMethods = goLangFields.map { f =>
      s"""func ($implTypeName *$implTypeName) ${f.name.capitalize}() ${f.fieldType.render()} {
         |${padding}return $implTypeName.${f.name}
         |}
       """.stripMargin
    }.mkString("\n")

    structImpl ++ Seq(constructor, implMethods)
  }

  def renderAdt(i: Adt): Seq[String] = {
    Seq(s"adt: ${i.id.name}")
  }

  private def toModuleId(defn: ILAst): ModuleId = {
    defn match {
      case i: Alias =>
        val concrete = i.id
        ModuleId(concrete.pkg, s"${concrete.pkg.last}.go")

      case other =>
        val id = other.id
        toModuleId(id)
    }
  }

  private def toModuleId(id: TypeId): ModuleId = {
    ModuleId(id.pkg, s"${id.name}.go")
  }

  private def toSource(id: Indefinite, moduleId: ModuleId, traitDef: Seq[String]) = {
    val code = traitDef.mkString("\n\n")
    val content: String = withPackage(id.pkg, code)
    Seq(Module(moduleId, content))
  }

  private def withPackage(pkg: idealingua.model.common.Package, code: String) = {
    val content = if (pkg.isEmpty) {
      code
    } else {
      s"""package ${pkg.last}
         |
         |$code
       """.stripMargin
    }
    content
  }
}
