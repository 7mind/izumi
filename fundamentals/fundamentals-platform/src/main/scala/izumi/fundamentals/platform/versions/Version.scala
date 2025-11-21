package izumi.fundamentals.platform.versions

import izumi.fundamentals.collections.nonempty.NEList

import scala.annotation.{nowarn, tailrec}
import scala.util.Try

sealed trait Version

@nowarn("msg=Iterables are not guaranteed to have a consistent order")
object Version {
  final case class Canonical(components: NEList[Int], qualifiers: List[String]) extends Version {
    def toSemver: Option[Semver] = {
      if (components.size == 3 && qualifiers.size < 2) {
        Some(Semver(components.head, components(1), components(2), qualifiers.headOption, None))
      } else {
        None
      }
    }

    override def toString: String = (components.mkString(".") +: qualifiers).mkString("-")
  }

  final case class Semver(major: Int, minor: Int, patch: Int, pre: Option[String], build: Option[String]) extends Version {
    def canonical: Canonical = Canonical(NEList(major, minor, patch), List(pre, build).flatten)

    override def toString: String = List(Some(s"$major.$minor.$patch"), pre.map(s => s"-$s"), build.map(s => s"+$s")).flatten.mkString
  }

  final case class Unknown(version: String) extends Version {
    override def toString: String = version
  }

  def parseSemver(version: String): Option[Semver] = {
    val semverPattern = """^(\d+)\.(\d+)\.(\d+)(?:-([a-zA-Z0-9.-]+))?(?:\+([a-zA-Z0-9.-]+))?$""".r
    version match {
      case semverPattern(major, minor, patch, pre, build) =>
        try {
          Some(
            Semver(
              major.toInt,
              minor.toInt,
              patch.toInt,
              Option(pre),
              Option(build),
            )
          )
        } catch {
          case _: NumberFormatException => None
        }
      case _ => None
    }
  }
  def parseCanonical(version: String): Option[Canonical] = {
    val parts = version.split("-", 2)
    val componentsPart = parts.head
    val qualifiersPart = if (parts.length > 1) parts(1).split("-").toList else List.empty

    val components = componentsPart.split("\\.").toList
    if (components.isEmpty) {
      None
    } else {
      try {
        val intComponents = components.map(_.toInt)
        NEList.from(intComponents) match {
          case Some(nel) => Some(Canonical(nel, qualifiersPart))
          case None => None
        }
      } catch {
        case _: NumberFormatException => None
      }
    }
  }
  def parse(version: String): Version = {
    parseSemver(version) match {
      case Some(semver) => semver
      case None =>
        parseCanonical(version) match {
          case Some(canonical) => canonical
          case None => Unknown(version)
        }
    }
  }

  object Canonical {
    implicit val canonicalOrder: Ordering[Canonical] = new Ordering[Canonical] {
      def compare(x: Canonical, y: Canonical): Int = {
        val componentComparison = compareComponents(x.components, y.components)
        if (componentComparison != 0) {
          componentComparison
        } else {
          compareQualifiers(x.qualifiers, y.qualifiers)
        }
      }

      private def compareComponents(x: NEList[Int], y: NEList[Int]): Int = {
        // not available on 2.12
        // Ordering.Implicits.seqOrdering[Seq, Int].compare(x.toList, y.toList)

        Ordering[Iterable[Int]].compare(x.toIterable, y.toIterable)
      }

      @tailrec
      private def compareQualifiers(x: List[String], y: List[String]): Int = {
        (x, y) match {
          case (Nil, Nil) => 0
          case (Nil, _) => 1 // No qualifier is considered newer than having qualifiers
          case (_, Nil) => -1
          case (xh :: xt, yh :: yt) =>
            val cmp = xh.compare(yh)
            if (cmp != 0) cmp else compareQualifiers(xt, yt)
        }
      }
    }
  }

  object Semver {

    implicit val semverOrder: Ordering[Semver] = new Ordering[Semver] {
      def compare(x: Semver, y: Semver): Int = {
        val majorCmp = x.major.compare(y.major)
        if (majorCmp != 0) return majorCmp

        val minorCmp = x.minor.compare(y.minor)
        if (minorCmp != 0) return minorCmp

        val patchCmp = x.patch.compare(y.patch)
        if (patchCmp != 0) return patchCmp

        comparePreRelease(x.pre, y.pre)
      }

      private def comparePreRelease(x: Option[String], y: Option[String]): Int = {
        (x, y) match {
          case (None, None) => 0
          case (None, Some(_)) => 1 // No pre-release is considered newer than having pre-release
          case (Some(_), None) => -1
          case (Some(xPre), Some(yPre)) => comparePreReleaseIdentifiers(xPre, yPre)
        }
      }

      private def comparePreReleaseIdentifiers(x: String, y: String): Int = {
        val xParts = x.split("\\.")
        val yParts = y.split("\\.")

        @tailrec
        def compareParts(i: Int): Int = {
          if (i >= xParts.length && i >= yParts.length) 0
          else if (i >= xParts.length) -1
          else if (i >= yParts.length) 1
          else {
            val xPart = xParts(i)
            val yPart = yParts(i)

            (Try(xPart.toInt).toOption, Try(yPart.toInt).toOption) match {
              case (Some(xNum), Some(yNum)) =>
                val cmp = xNum.compare(yNum)
                if (cmp != 0) cmp else compareParts(i + 1)
              case (Some(_), None) => -1 // Numeric identifiers have lower precedence
              case (None, Some(_)) => 1
              case (None, None) =>
                val cmp = xPart.compare(yPart)
                if (cmp != 0) cmp else compareParts(i + 1)
            }
          }
        }

        compareParts(0)
      }
    }
  }

  object Unknown {
    implicit val unknownOrder: Ordering[Unknown] = new Ordering[Unknown] {
      def compare(x: Unknown, y: Unknown): Int = x.version.compare(y.version)
    }
  }

  implicit val versionOrder: Ordering[Version] = new Ordering[Version] {
    def compare(x: Version, y: Version): Int = {
      (x, y) match {
        case (xc: Canonical, yc: Canonical) => Canonical.canonicalOrder.compare(xc, yc)
        case (xs: Semver, ys: Semver) => Semver.semverOrder.compare(xs, ys)
        case (xu: Unknown, yu: Unknown) => Unknown.unknownOrder.compare(xu, yu)

        case (xs: Semver, yc: Canonical) => Canonical.canonicalOrder.compare(xs.canonical, yc)
        case (xc: Canonical, ys: Semver) => Canonical.canonicalOrder.compare(xc, ys.canonical)

        case (xc: Canonical, yu: Unknown) => xc.toString.compare(yu.version)
        case (xu: Unknown, yc: Canonical) => xu.version.compare(yc.toString)

        case (xs: Semver, yu: Unknown) => xs.toString.compare(yu.version)
        case (xu: Unknown, ys: Semver) => xu.version.compare(ys.toString)
      }
    }
  }
}
