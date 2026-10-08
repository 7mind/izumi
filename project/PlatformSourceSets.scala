import sbt.*
import sbt.Keys.*
import sbtcrossproject.CrossPlugin.autoImport.crossProjectBaseDirectory

object PlatformSourceSets {
  def settings(names: String*): Seq[Setting[Seq[File]]] = Seq(Compile -> "main", Test -> "test").map {
    case (configuration, source) =>
      configuration / unmanagedSourceDirectories ++= {
        val root = crossProjectBaseDirectory.?.value.getOrElse(baseDirectory.value)
        val major = scalaBinaryVersion.value.takeWhile(_ != '.')
        names.flatMap(name => Seq(root / "src" / source / s"scala-$name", root / "src" / source / s"scala-$name-$major"))
      }
  }
}
