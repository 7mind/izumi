package izumi.idealingua

import java.util._

import izumi.idealingua.runtime.model._
import org.scalatest.wordspec.AnyWordSpec

class IdTest extends AnyWordSpec {
  import IdTest._

  "Identifiers" should {
    "support complex serialization" in {
      val id = ComplexID(
        BucketID(UUID.randomUUID(), UUID.randomUUID(), "test")
        , UserWithEnumId(UUID.randomUUID(), UUID.randomUUID(), DepartmentEnum.Engineering)
        , UUID.randomUUID()
        , "field"
      )

      val serialized = id.toString
      val parsed = ComplexID.parse(serialized)
      assert(parsed == id)
    }
  }
}


object IdTest {
  sealed trait DepartmentEnum extends IDLEnumElement

  object DepartmentEnum extends IDLEnum {
    type Element = DepartmentEnum
    override def all: Seq[DepartmentEnum] = Seq(Engineering, Sales)
    override def parse(value: String): DepartmentEnum = value match {
      case "Engineering" => Engineering
      case "Sales" => Sales
    }
    final case object Engineering extends DepartmentEnum { override def toString: String = "Engineering" }
    final case object Sales extends DepartmentEnum { override def toString: String = "Sales" }
  }


  final case class UserWithEnumId(value: UUID, company: UUID, dept: DepartmentEnum) extends IDLGeneratedType with IDLIdentifier {
    override def toString: String = {
      import izumi.idealingua.runtime.model.IDLIdentifier._
      val suffix = Seq(this.company, this.dept, this.value).map(part => escape(part.toString)).mkString(":")
      s"UserWithEnumId#$suffix"
    }
  }

  object UserWithEnumId {
    def parse(s: String): UserWithEnumId = {
      import izumi.idealingua.runtime.model.IDLIdentifier._
      val withoutPrefix = s.substring(s.indexOf("#") + 1)
      val parts = withoutPrefix.split(':').map(part => unescape(part))
      UserWithEnumId(company = parsePart[UUID](parts(0), classOf[UUID]), dept = DepartmentEnum.parse(parts(1)), value = parsePart[UUID](parts(2), classOf[UUID]))
    }
  }


  final case class BucketID(app: UUID, user: UUID, bucket: String) extends IDLGeneratedType with IDLIdentifier {
    override def toString: String = {
      import izumi.idealingua.runtime.model.IDLIdentifier._
      val suffix = Seq(this.app, this.bucket, this.user).map(part => escape(part.toString)).mkString(":")
      s"BucketID#$suffix"
    }
  }

  object BucketID {
    def parse(s: String): BucketID = {
      import izumi.idealingua.runtime.model.IDLIdentifier._
      val withoutPrefix = s.substring(s.indexOf("#") + 1)
      val parts = withoutPrefix.split(':').map(part => unescape(part))
      BucketID(app = parsePart[UUID](parts(0), classOf[UUID]), bucket = parsePart[String](parts(1), classOf[String]), user = parsePart[UUID](parts(2), classOf[UUID]))
    }
  }


  final case class ComplexID(bucket: BucketID, user: UserWithEnumId, uid: UUID, str: String) extends IDLGeneratedType with IDLIdentifier {
    override def toString: String = {
      import izumi.idealingua.runtime.model.IDLIdentifier._
      val suffix = Seq(this.bucket, this.str, this.uid, this.user).map(part => escape(part.toString)).mkString(":")
      s"ComplexID#$suffix"
    }
  }

  object ComplexID {
    def parse(s: String): ComplexID = {
      import izumi.idealingua.runtime.model.IDLIdentifier._
      val withoutPrefix = s.substring(s.indexOf("#") + 1)
      val parts = withoutPrefix.split(':').map(part => unescape(part))
      ComplexID(bucket = BucketID.parse(parts(0)), str = parsePart[String](parts(1), classOf[String]), uid = parsePart[UUID](parts(2), classOf[UUID]), user = UserWithEnumId.parse(parts(3)))
    }
  }
}
