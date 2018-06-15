package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSTContext
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

object NUnitExtension extends CSharpTranslatorExtension {
  //  override def handleEnum(ctx: TSTContext, enum: TypeDef.Enumeration, product: EnumProduct): EnumProduct = {
  //    val it = enum.members.iterator
  //    val values = it.map { m =>
  //      s"${enum.id.name}.${m}" + (if (it.hasNext) "," else "")
  //    }.mkString("\n")
  //
  //    val extension =
  //      s"""
  //         |export class ${enum.id.name}Helpers {
  //         |    public static readonly all = [
  //         |${values.shift(8)}
  //         |    ]
  //         |
  //         |    public static isValid(value: string): boolean {
  //         |        return ${enum.id.name}Helpers.all.indexOf(value as ${enum.id.name}) >= 0;
  //         |    }
  //         |}
  //       """.stripMargin
  //
  //    EnumProduct(product.content + extension, product.preamble)
  //  }
}


/*
// mcs -target:library -r:nunit.framework -out:Tests.dll Greeter.cs Tests.cs
using NUnit.Framework;
nunit-console Tests.dll

public class Greeter
{
    public static string Hello()
    {
        return "Hello world.";
    }
}

[TestFixture]
public class HelloTest
{
    [Test]
    public void Stating_something ()
    {
        Assert.AreEqual("Hello world.", Greeter.Hello());
    }
}
 */

/*

using NUnit.Framework;
using irt;

namespace idltest.enums
{
    [TestFixture]
    public class TestEnum_ShouldSerialize
    {
        IJsonMarshaller marshaller;
        public TestEnum_ShouldSerialize() {
            marshaller = new JsonNetMarshaller();
        }

        [Test]
        public void Serialize() {
            var v = TestEnum.Element1;
            var json = marshaller.Marshal<TestEnum>(v);
            Assert.AreEqual("\"Element1\"", json);
        }

        [Test]
        public void Deserialize() {
            var v = marshaller.Unmarshal<TestEnum>("\"Element1\"");
            Assert.AreEqual(v, TestEnum.Element1);
        }

        [Test]
        public void SerializeDeserialize() {
            var v1 = TestEnum.Element1;
            var json = marshaller.Marshal<TestEnum>(v1);
            var v2 = marshaller.Unmarshal<TestEnum>(json);
            Assert.AreEqual(v1, v2);
        }
    }
}
 */

/*

using NUnit.Framework;
using irt;
using System;

namespace idltest.identifiers
{
    [TestFixture]
    public class BucketID_ShouldSerialize
    {
        IJsonMarshaller marshaller;

        public BucketID_ShouldSerialize() {
            marshaller = new JsonNetMarshaller();
        }

        [Test]
        public void SerializeDeserialize() {
            var v1 = new BucketID(new Guid("ee9be762-ab1c-4f1e-ab73-a98dd1312fa3"), new Guid("2800324c-4235-415b-937c-d7d0bbb65b26"), "str");
            var json1 = marshaller.Marshal<BucketID>(v1);
            var v2 = marshaller.Unmarshal<BucketID>(json1);
            var json2 = marshaller.Marshal<BucketID>(v2);
            Assert.AreEqual(v1.ToString(), v2.ToString());
            Assert.AreEqual(json1.ToString(), json2.ToString());
        }
    }
}
 */