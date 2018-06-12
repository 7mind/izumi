package com.github.pshirshov.izumi.idealingua.translator.tocsharp.types

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{Generic, Primitive, TypeId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.{Alias, Enumeration, Interface}
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.csharp.types.CSharpField
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpImports
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Structure
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.Struct

final case class CSharpClass (
                              id: TypeId,
                              name: String,
                              fields: Seq[CSharpField],
                              implements: List[InterfaceId] = List.empty
                             )(implicit im: CSharpImports, ts: Typespace) {

  def renderHeader(): String = {
    val impls = if (implements.isEmpty) "" else " : " + implements.map(i => i.name).mkString(", ")
    s"public class $name$impls"
  }

  def render(withWrapper: Boolean, withSlices: Boolean): String = {
      val indent = if (withWrapper) 4 else 0

      s"""${if (withWrapper) s"${renderHeader()} {" else ""}
         |${fields.map(f => f.renderMember(false)).mkString("\n").shift(indent)}
         |${if (withSlices) ("\n" + renderSlices()).shift(indent) else ""}
         |${if (withWrapper) "}" else ""}
       """.stripMargin
  }

  private def renderSlice(i: InterfaceId): String = {
    val interface = ts(i).asInstanceOf[Interface]
    val eid = ts.implId(i)
    val eidStruct = ts.structure.structure(eid)
    val eidClass = CSharpClass(eid, i.name + eid.name, eidStruct, List.empty)

    s"""public ${i.name} To${i.name}() {
       |    var res = new ${i.name}${eid.name}();
       |${eidClass.fields.map(f => s"res.${f.renderMemberName()} = this.${f.renderMemberName()};").mkString("\n").shift(4)}
       |    return res;
       |}
     """.stripMargin
  }

  private def renderSlices(): String = {
    implements.map(i => renderSlice(i)).mkString("\n")
  }
}

object CSharpClass {
  def apply(
             id: TypeId,
             name: String,
             fields: Seq[CSharpField],
             implements: List[InterfaceId] = List.empty
           )(implicit im: CSharpImports, ts: Typespace): CSharpClass = new CSharpClass(id, name, fields, implements)

  def apply(id: TypeId, name: String, st: Struct, implements: List[InterfaceId])(implicit im: CSharpImports, ts: Typespace): CSharpClass =
    new CSharpClass(id, name, st.all.groupBy(_.field.name)
      .map(f => CSharpField(f._2.head.field, name, if (f._2.length > 1) f._2.map(ef => ef.defn.definedBy.name) else Seq.empty)).toSeq,
      st.superclasses.interfaces ++ implements)
}
