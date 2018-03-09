package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common.{Indefinite, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.EphemeralId
import com.github.pshirshov.izumi.idealingua.model.il.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il.{ILAst, Typespace}
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}

import scala.collection.mutable
import GoLangTypeConverter._
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Service.DefMethod.RPCMethod

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
      } ++
      typespace.domain.services.flatMap(translateService)
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

    Seq(s"""
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
    val fields = typespace.enumFields(typespace.getComposite(id))

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

  def renderAdt(i: Adt) = Seq(s"adt: ${i.id.name}")

  protected def translateService(definition: Service): Seq[Module] = {
    toSource(Indefinite(definition.id), toModuleId(definition.id), renderService(definition))
  }

  protected def renderService(i: Service): Seq[String] = {
    val (methods, inTypes, outTypes) = i.methods.map {
      case method: RPCMethod =>
        // TODO: unify with ephemerals in typespace
        val in = s"In${method.name.capitalize}"
        val out = s"Out${method.name.capitalize}"

        val inDef = EphemeralId(i.id, in)
        val outDef = EphemeralId(i.id, out)

        val inDefSrc = renderStruct(inDef.name, typespace.enumFields(typespace.getComposite(inDef)).toGoLangFields.all)
        val outDefSrc = renderStruct(outDef.name, typespace.enumFields(typespace.getComposite(outDef)).toGoLangFields.all)
        val methodSrc = s"${method.name}(${inDef.name}) ${outDef.name}"

        (inDefSrc, outDefSrc, methodSrc)
    } match {
      case xs =>  (xs.map(padding + _._3).mkString("\n"), xs.map(_._1), xs.map(_._2))
    }

    val serviceInterfaceSrc = Seq(
      s"""type ${i.id.name} interface {
         |$methods
         |}""".stripMargin
    )

    inTypes.flatten ++ outTypes.flatten ++ serviceInterfaceSrc ++ renderServiceTransport(i)
  }

  private def renderServiceTransport(i: Service): Seq[String] = {
    Seq.empty
  }

  private def toModuleId(defn: ILAst): ModuleId = {
    defn match {
      case i: Alias => ModuleId(i.id.pkg, s"${i.id.pkg.last}.go")
      case other => toModuleId(other.id)
    }
  }

  private def toModuleId(id: TypeId) = ModuleId(id.pkg, s"${id.name}.go") // use implicits conv

  private def toSource(id: Indefinite, moduleId: ModuleId, traitDef: Seq[String]) = {
    val code = traitDef.mkString("\n\n")
    val content = withPackage(id.pkg, code)
    Seq(Module(moduleId, content))
  }

  private def withPackage(pkg: idealingua.model.common.Package, code: String) = {
    if (pkg.isEmpty) {
      code
    } else {
      s"""package ${pkg.last}
         |
         |$code
       """.stripMargin
    }
  }
}
