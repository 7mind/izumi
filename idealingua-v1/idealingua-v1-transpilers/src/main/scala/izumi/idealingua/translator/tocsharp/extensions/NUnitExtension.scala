package izumi.idealingua.translator.tocsharp.extensions

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.model.common.TypeId.InterfaceId
import izumi.idealingua.model.il.ast.typed.TypeDef.{Adt, DTO, Enumeration, Identifier}
import izumi.idealingua.model.output.Module
import izumi.idealingua.model.typespace.Typespace
import izumi.idealingua.translator.tocsharp.types.{CSharpClass, CSharpType}
import izumi.idealingua.translator.tocsharp.{CSTContext, CSharpImports}


object NUnitExtension extends CSharpTranslatorExtension {
  override def postEmitModules(ctx: CSTContext, id: Enumeration)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
    val name = id.id.name
    val testMember = id.members.head.value
    val code =
        s"""public static class ${name}TestHelper {
           |    public static $name Create() {
           |        return $name.$testMember;
           |    }
           |}
           |
           |[TestFixture]
           |public class ${name}_ShouldSerialize {
           |    IJsonMarshaller marshaller;
           |    public ${name}_ShouldSerialize() {
           |        marshaller = new JsonNetMarshaller();
           |    }
           |
           |    [Test]
           |    public void Serialize() {
           |        var v = ${name}TestHelper.Create();
           |        var json = marshaller.Marshal<$name>(v);
           |        Assert.AreEqual("\\"$testMember\\"", json);
           |    }
           |
           |    [Test]
           |    public void Deserialize() {
           |        var v = marshaller.Unmarshal<$name>("\\"$testMember\\"");
           |        Assert.AreEqual(v, $name.$testMember);
           |    }
           |
           |    [Test]
           |    public void SerializeDeserialize() {
           |        var v1 = ${name}TestHelper.Create();
           |        var json = marshaller.Marshal<$name>(v1);
           |        var v2 = marshaller.Unmarshal<$name>(json);
           |        Assert.AreEqual(v1, v2);
           |    }
           |}
         """.stripMargin

    val header =
      """using IRT;
        |using IRT.Marshaller;
        |using NUnit.Framework;
       """.stripMargin

    ctx.modules.toTestSource(id.id.path.domain, ctx.modules.toTestModuleId(id.id), header, code)
  }

  override def postEmitModules(ctx: CSTContext, id: Identifier)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
    val name = id.id.name
    val code =
      s"""public static class ${name}TestHelper {
         |    public static $name Create() {
         |        return new $name(${id.fields.map(f => CSharpType(f.typeId).getRandomValue(0)).mkString(", ")});
         |    }
         |}
         |
         |[TestFixture]
         |public class ${name}_ShouldSerialize {
         |    IJsonMarshaller marshaller;
         |
         |    public ${name}_ShouldSerialize() {
         |        marshaller = new JsonNetMarshaller();
         |    }
         |
         |    [Test]
         |    public void SerializeDeserialize() {
         |        var v1 = ${name}TestHelper.Create();
         |        var json1 = marshaller.Marshal<$name>(v1);
         |        var v2 = marshaller.Unmarshal<$name>(json1);
         |        var json2 = marshaller.Marshal<$name>(v2);
         |        Assert.AreEqual(v1.ToString(), v2.ToString());
         |        Assert.AreEqual(json1.ToString(), json2.ToString());
         |    }
         |}
       """.stripMargin

    val header =
      """using IRT;
        |using IRT.Marshaller;
        |using NUnit.Framework;
       """.stripMargin

    ctx.modules.toTestSource(id.id.path.domain, ctx.modules.toTestModuleId(id.id), header, code)
  }

  override def postEmitModules(ctx: CSTContext, i: Adt)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
    val name = i.id.name
    val adt = i.alternatives.head
    val testValue =
      if (adt.typeId.isInstanceOf[InterfaceId])
        s"new ${CSharpType(adt.typeId).renderType(true)}Struct()"
    else
      s"${CSharpType(adt.typeId).renderType(true)}TestHelper.Create()"

    val code =
      s"""public static class ${name}TestHelper {
         |    public static $name Create() {
         |        return new $name.${adt.wireId}($testValue);
         |    }
         |}
         |
         |[TestFixture]
         |public class ${name}_ShouldSerialize {
         |    IJsonMarshaller marshaller;
         |
         |    public ${name}_ShouldSerialize() {
         |        marshaller = new JsonNetMarshaller();
         |    }
         |
         |    [Test]
         |    public void SerializeDeserialize() {
         |        var v1 = ${name}TestHelper.Create();
         |        var json1 = marshaller.Marshal<$name>(v1);
         |        var v2 = marshaller.Unmarshal<$name>(json1);
         |        var json2 = marshaller.Marshal<$name>(v2);
         |        Assert.AreEqual(v1.ToString(), v2.ToString());
         |        Assert.AreEqual(json1.ToString(), json2.ToString());
         |    }
         |}
       """.stripMargin

    val header =
      """using IRT;
        |using IRT.Marshaller;
        |using System;
        |using System.Globalization;
        |using System.Collections;
        |using System.Collections.Generic;
        |using NUnit.Framework;
      """.stripMargin

    ctx.modules.toTestSource(i.id.path.domain, ctx.modules.toTestModuleId(i.id), header, code)
  }

  override def postEmitModules(ctx: CSTContext, i: DTO)(implicit im: CSharpImports, ts: Typespace): Seq[Module] = {
    val implIface = ts.inheritance.allParents(i.id).find(ii => ts.tools.implId(ii) == i.id)
    val dtoName = if (implIface.isDefined) implIface.get.name + i.id.name else i.id.name

    val structure = ts.structure.structure(i)
    val struct = CSharpClass(i.id, i.id.name, structure, List.empty)

    val code =
      s"""public static class ${dtoName}TestHelper {
         |    public static $dtoName Create() {
         |        return new $dtoName(
         |${struct.fields.map(f => f.tp.getRandomValue(3)).mkString(",\n").shift(12)}
         |        );
         |    }
         |}
         |
         |[TestFixture]
         |public class ${dtoName}_ShouldSerialize {
         |    IJsonMarshaller marshaller;
         |
         |    public ${dtoName}_ShouldSerialize() {
         |        marshaller = new JsonNetMarshaller();
         |    }
         |
         |    [Test]
         |    public void SerializeDeserialize() {
         |        var v1 = ${dtoName}TestHelper.Create();
         |        var json1 = marshaller.Marshal<$dtoName>(v1);
         |        var v2 = marshaller.Unmarshal<$dtoName>(json1);
         |        var json2 = marshaller.Marshal<$dtoName>(v2);
         |        Assert.AreEqual(v1.ToString(), v2.ToString());
         |        Assert.AreEqual(json1.ToString(), json2.ToString());
         |    }
         |}
       """.stripMargin

    val header =
      """using IRT;
         |using IRT.Marshaller;
         |using System;
         |using System.Globalization;
         |using System.Collections;
         |using System.Collections.Generic;
         |using NUnit.Framework;
       """.stripMargin
    ctx.modules.toTestSource(i.id.path.domain, ctx.modules.toTestModuleId(i.id, if(implIface.isDefined) Some(implIface.get.name) else None), header, code)
  }
}

