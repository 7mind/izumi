package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks.discard
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.{CSTContext, CSharpImports}
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.{Enumeration, Identifier}
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.types.CSharpType


object NUnitExtension extends CSharpTranslatorExtension {
  override def postEmitModules(ctx: CSTContext, id: Enumeration): Seq[Module] = {
    val name = id.id.name
    val testMember = id.members.head
    val code =
        s"""[TestFixture]
           |public class ${name}_ShouldSerialize {
           |    IJsonMarshaller marshaller;
           |    public ${name}_ShouldSerialize() {
           |        marshaller = new JsonNetMarshaller();
           |    }
           |
           |    [Test]
           |    public void Serialize() {
           |        var v = ${name}.${testMember};
           |        var json = marshaller.Marshal<${name}>(v);
           |        Assert.AreEqual("\\"${testMember}\\"", json);
           |    }
           |
           |    [Test]
           |    public void Deserialize() {
           |        var v = marshaller.Unmarshal<${name}>("\\"${testMember}\\"");
           |        Assert.AreEqual(v, ${name}.${testMember});
           |    }
           |
           |    [Test]
           |    public void SerializeDeserialize() {
           |        var v1 = ${name}.${testMember};
           |        var json = marshaller.Marshal<${name}>(v1);
           |        var v2 = marshaller.Unmarshal<${name}>(json);
           |        Assert.AreEqual(v1, v2);
           |    }
           |}
         """.stripMargin

    val header =
      s"""using irt;
         |using NUnit.Framework;
       """.stripMargin

    ctx.modules.toTestSource(id.id.path.domain, ctx.modules.toTestModuleId(id.id), header, code)
  }

  override def postEmitModules(ctx: CSTContext, id: Identifier): Seq[Module] = {
    implicit val ts: Typespace = ctx.typespace
    implicit val im: CSharpImports = new CSharpImports()

    val name = id.id.name
    val code =
      s"""[TestFixture]
         |public class ${name}_ShouldSerialize {
         |    IJsonMarshaller marshaller;
         |
         |    public ${name}_ShouldSerialize() {
         |        marshaller = new JsonNetMarshaller();
         |    }
         |
         |    [Test]
         |    public void SerializeDeserialize() {
         |        var v1 = new $name(${id.fields.map(f => CSharpType(f.typeId).getRandomValue).mkString(", ")});
         |        var json1 = marshaller.Marshal<$name>(v1);
         |        var v2 = marshaller.Unmarshal<$name>(json1);
         |        var json2 = marshaller.Marshal<$name>(v2);
         |        Assert.AreEqual(v1.ToString(), v2.ToString());
         |        Assert.AreEqual(json1.ToString(), json2.ToString());
         |    }
         |}
       """.stripMargin

    val header =
      s"""using irt;
         |using NUnit.Framework;
       """.stripMargin

    ctx.modules.toTestSource(id.id.path.domain, ctx.modules.toTestModuleId(id.id), header, code)
  }
}