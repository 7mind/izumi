package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common.{Indefinite, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{EphemeralId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.il.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il.{ILAst, Typespace}
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}

import scala.collection.{GenTraversableOnce, mutable}
import GoLangTypeConverter._
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Service.DefMethod.RPCMethod

class GoLangTranslator(typespace: Typespace) {
  val padding: String = " " * 4

  protected val packageObjects: mutable.HashMap[ModuleId, mutable.ArrayBuffer[String]] = {
    mutable.HashMap[ModuleId, mutable.ArrayBuffer[String]]()
  }

  def renderBaseServicesFunc(): Seq[String] = {
    Seq(
      withImports("io", "net/http", "crypto/tls", "time", "io/ioutil", "encoding/json", "fmt"),
      s"""
         |func makeHTTPRequest(method string, path string, body io.Reader, dataOut interface{}) (*http.Response, error) {
         |	url := path
         |
         |	req, err := http.NewRequest(method, url, body)
         |	if err != nil {
         |		return nil, err
         |	}
         |
         |	if body != nil {
         |		req.Header.Set("Content-Type", "application/json")
         |	}
         |
         |	tr := &http.Transport{
         |		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
         |		ExpectContinueTimeout: time.Millisecond * 10000,
         |		ResponseHeaderTimeout: time.Millisecond * 10000,
         |	}
         |
         |	client := &http.Client{Transport: tr}
         |	resp, err := client.Do(req)
         |	if err != nil {
         |		return resp, err
         |	}
         |
         |	defer resp.Body.Close()
         |	respBody, err := ioutil.ReadAll(resp.Body)
         |	if err != nil {
         |		return resp, err
         |	}
         |
         |	if err := json.Unmarshal(respBody, &dataOut); err != nil {
         |		return resp, fmt.Errorf("Error: %+v Body: %s", err, string(respBody))
         |	}
         |
         |	return resp, nil
         |}
       """.stripMargin)
  }

  def translate(): Seq[Module] = {
    // if there is some services defined, we put common functions in package object
    typespace.domain.services.foreach { i =>
      packageObjects.getOrElseUpdate(ModuleId(i.id.pkg, s"${i.id.pkg.last}.go"), mutable.ArrayBuffer())  ++=
        renderBaseServicesFunc()
    }

    typespace.domain
      .types
      .flatMap(translateType) ++
      packageObjects.map {
        case (id, content) =>
          val code =
            s"""
               |${content.map(_.toString()).mkString("\n")}
           """.stripMargin
          Module(id, withPackage(id.path, code))
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
        renderDTO(d)
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
    Seq(s"type ${i.id.name} ${toGoLang(i.target).render()}")
  }

  def renderEnumeration(i: Enumeration): Seq[String] = {
    val name = i.id.name
    val firstChar = name.charAt(0).toLower

    val values = (i.members match {
      case first :: rest => s"$first $name = iota" +: rest
    }).map(v => padding + v).mkString("\n")

    val dataTypesId = i.members.map { m =>
      s"""$m: "$m","""
    }.map(v => padding + v).mkString("\n")

    val dataTypesName = i.members.map { m =>
      s""""$m": $m,"""
    }.map(v => padding + v).mkString("\n")

    Seq(withImports("encoding/json", "bytes"), s"""
       |type $name int8
       |
       |const (
       |$values
       |)
       |
       |func ($firstChar $name) String() string {
       |    return dataTypesId[$firstChar]
       |}
       |
       |var dataTypesId = map[$name]string{
       |$dataTypesId
       |}
       |
       |var dataTypesName = map[string]$name{
       |$dataTypesName
       |}
       |
       |func ($firstChar $name) MarshalJSON() ([]byte, error) {
       |	  buffer := bytes.NewBufferString(`"`)
       |	  buffer.WriteString(dataTypesId[$firstChar])
       |	  buffer.WriteString(`"`)
       |	  return buffer.Bytes(), nil
       |}
       |
       |func ($firstChar *$name) UnmarshalJSON(b []byte) error {
       |	  // unmarshal as string
       |	  var s string
       |	  err := json.Unmarshal(b, &s)
       |	  if err != nil {
       |		  return err
       |	  }
       |
       |	  *$firstChar = dataTypesName[s]
       |	  return nil
       |}
     """.stripMargin
    )
  }

  protected def renderIdentifier(i: Identifier): Seq[String] = {
    renderStruct(i.id.name, typespace.enumFields(i).toGoLangFields.all)
  }

  protected def renderDTO(i: DTO): Seq[String] = {
    renderStruct(i.id.name, typespace.enumFields(i).toGoLangFields.all)
  }

  private def renderStruct(name: String, fields: List[GoLangField], withPrivate: Boolean = false) = {
    val fieldsStr = fields.map { f =>
      val fieldName = if (withPrivate) f.name else f.name.capitalize
      s"""$padding$fieldName  ${f.fieldType.render()} `json:"${f.name}"`"""
    }.mkString("\n")

    Seq(s"""
       |// $name struct
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
    ) ++ renderComposite(implType, i)
  }

  private def renderComposite(impl: TypeId, i: Interface): Seq[String] = {
    val implTypeName = impl.name
    val fields = typespace.enumFields(typespace.getComposite(impl))

    val goLangFields = fields.toGoLangFields.all

    val structImpl = renderStruct(implTypeName, goLangFields, withPrivate = true)

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
      s"""func New${impl.name}($constructorArgs) ${i.id.name} {
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
    val transportDef: Service = definition.copy(id = definition.id.copy(name = definition.id.name + "Transport")  )

    toSource(Indefinite(definition.id), toModuleId(definition.id), renderService(definition)) ++
    toSource(Indefinite(transportDef.id), toModuleId(transportDef.id), renderTransport(transportDef))
  }

  protected def renderService(i: Service): Seq[String] = {
    val (methods, inTypes, outTypes) = i.methods.map {
      case method: RPCMethod =>
        val in = s"In${method.name.capitalize}"
        val out = s"Out${method.name.capitalize}"

        val inDef = EphemeralId(i.id, in)
        val outDef = EphemeralId(i.id, out)

        val inDefSrc = renderStruct(inDef.name, typespace.enumFields(typespace.getComposite(inDef)).toGoLangFields.all)
        val outDefSrc = renderStruct(outDef.name, typespace.enumFields(typespace.getComposite(outDef)).toGoLangFields.all)
        val methodSrc = s"${method.name.capitalize}(${inDef.name}) ${outDef.name}"

        (inDefSrc, outDefSrc, methodSrc)
    } match {
      case xs =>  (xs.map(padding + _._3).mkString("\n"), xs.map(_._1), xs.map(_._2))
    }

    val serviceInterfaceSrc = Seq(
      s"""type ${i.id.name} interface {
         |$methods
         |}""".stripMargin
    )

    Seq(withImports("encoding/json")) ++ inTypes.flatten ++ outTypes.flatten ++ serviceInterfaceSrc ++ renderClient(i)
  }

  protected def renderTransport(i: Service): Seq[String] = {
    val serviceName = i.id.name
    val implName = s"${serviceName}Impl"

    val impl = renderStruct(implName, List(GoLangField("endpoint", StringType())))

    val interface =  s"""type $serviceName interface {
      |${padding}Send(service string, in map[string]interface{}) interface{}
      |}""".stripMargin

    val constructor =
      s"""
         |// New$serviceName(endpobint string) impl
         |func New$serviceName(endpoint string) $serviceName {
         |${padding}return &$implName {
         |${padding}Endpoint: endpoint,
         |$padding}
         |}""".stripMargin

    val methodBody: String =
      s"""jsonStr, _ := json.Marshal(in)
         |body := bytes.NewBuffer(jsonStr)
         |var responseBody interface{}
         |_, err := makeHTTPRequest("POST", $implName.Endpoint + "?service=" + service, body, &responseBody)
         |if err != nil {
         |${padding}return nil
         |}
         |return &responseBody
       """.stripMargin.split("\n").map(padding + _).mkString("\n")

    val methods = s"""
           |// Send impl
           |func ($implName *$implName) Send(service string, in map[string]interface{}) interface{} {
           |$methodBody
           |}
       """.stripMargin

    Seq(withImports("encoding/json", "bytes")) ++ Seq(interface) ++ impl ++ Seq(constructor, methods)
  }

  private def renderClient(i: Service): Seq[String] = {
    val serviceName = i.id.name
    val implName = s"${serviceName}Impl"                                                 
    val transportName = s"${serviceName}Transport"

    val impl = renderStruct(implName, List(GoLangField("transport", GoLangInterface(transportName))))

    val constructor =
      s"""
         |func New$serviceName(transport $transportName) $serviceName {
         |${padding}return &$implName {
         |$padding${padding}Transport: transport,
         |$padding}
         |}""".stripMargin

    val methods = i.methods.map {
      case m: RPCMethod =>
        val in = s"In${m.name.capitalize}"
        val out = s"Out${m.name.capitalize}"

        val methodBody =
          s"""var inInterface map[string]interface{}
            |inrec, _ := json.Marshal(in)
            |json.Unmarshal(inrec, &inInterface)
            |inInterface["inputType"] = "$in"
            |
            |out := $implName.Transport.Send("$serviceName", inInterface)
            |outByte, _ := json.Marshal(out)
            |result := $out{}
            |json.Unmarshal(outByte, &result)
            |return result
          """.stripMargin.split("\n").map(padding + _).mkString("\n")

        s"""
         |func ($implName *$implName) ${m.name.capitalize}(in $in) $out {
         |$methodBody
         |}
        """.stripMargin
    }.mkString("\n")

    impl ++ Seq(constructor, methods)
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

  private def withImports(imports: String*): String = {
    val importsStr = imports.map(module => s"""$padding"$module"""").mkString("\n")

    s"""import (
       |$importsStr
       |)""".stripMargin
  }
}
