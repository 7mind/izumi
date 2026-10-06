# Draft: compiler dependencies cross to JS/Native under SBT 2

This report has not been submitted. Reproduced on 2026-10-06 with SBT 2.0.9,
Scala 2.13.18, JDK 21, sbt-scoverage 2.4.4, Scala.js 1.22.0 and Scala Native
0.5.12. No izumi artifacts or plugins are required.

Create `project/build.properties`:

```properties
sbt.version=2.0.9
```

Create `project/plugins.sbt`:

```scala
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
```

Create `build.sbt`:

```scala
scalaVersion := "2.13.18"
lazy val root = project.in(file(".")).enablePlugins(ScalaJSPlugin)
```

For the separate Native reproduction, replace `ScalaJSPlugin` with
`ScalaNativePlugin`. Create `src/main/scala/Witness.scala`:

```scala
object Witness { def choose(value: Boolean): Int = if (value) 1 else 2 }
```

Run `sbt --server -batch compile coverage compile`. On both platforms the
ordinary compile succeeds, then the instrumented compile exits 1 during
dependency resolution. JS requests the unavailable
`scalac-scoverage-{domain,reporter,serializer}_sjs1_2.13:2.5.2`; Native requests
the corresponding `_native0.5_2.13` artifacts. Expected: compiler-side
dependencies resolve to JVM artifacts while the instrumentation runtime uses
the execution target's artifacts.

The [Scoverage plugin source at 2.4.4](https://github.com/scoverage/sbt-scoverage/blob/v2.4.4/src/main/scala/scoverage/ScoverageSbtPlugin.scala)
declares these compiler dependencies with `%%`. SBT 2's platform-aware crossing
is consistent with the observed suffixes. This explanation is an inference
from the source and resolver output; the failed resolution itself is observed.
Correcting reporter/serializer, then domain, also exposes the compiler plugin's
inappropriate platform suffix. A local build correction pins these four
compiler artifacts to JVM coordinates and the private compiler configuration,
preserving target runtime dependencies. The compiler plugin uses the full Scala
version suffix, and its three support artifacts use the binary Scala suffix.

Two minimal public reproductions have actual exit 1 after successful ordinary
compilation. Captures and exact command vectors:
`/srv/nvme/tmp/izumi-impl/3-scoverage-platform-public-reproduction/`.
The independent audit SHA256 is
`a02e94677926a963b20b1fac770ec1ee207df87fc8af58f7a28fc872437eb65b`.
Searches of the upstream tracker for SBT 2/Scala.js and the exact Native domain
artifact name found no matching report; that search does not establish absence
of an existing issue. The corrected six-lane izumi consumer fixture separately
passes, as recorded in the implementation status ledger.

## Separate JS macro runtime reproduction

After correcting compiler artifact resolution, an instrumented Scala 2 macro
helper can invoke the JS Scoverage runtime inside the JVM compiler. A separate
two-project build reproduces this without izumi sources. The `macros` project
defines:

```scala
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
object WitnessMacro {
  def position: String = macro WitnessMacroImpl.position
}
object WitnessMacroImpl {
  def applicationPoint(c: blackbox.Context): String = c.enclosingPosition.line.toString
  def position(c: blackbox.Context): c.Expr[String] = {
    import c.universe._
    c.Expr[String](Literal(Constant(applicationPoint(c))))
  }
}
```

An `app` project depends on `macros` and contains
`object MacroConsumer { val actual: String = WitnessMacro.position }`.
Both enable ScalaJSPlugin; `macros` adds the JVM scala-reflect 2.13.18 dependency
in Provided scope. The same `compile coverage compile` command first succeeds
ordinarily, then fails through `WitnessMacroImpl.applicationPoint ->
scoverage.Invoker -> scalajssupport.File`. The matching Native baseline
succeeds: the observed JS failure is not an observed Native failure.

The correction adds the JVM Scoverage runtime to the private compiler
configuration and supplies `-Ymacro-classpath` with the ordinary dependency
classpath, replacing its target Scoverage runtime jar with that JVM jar. Target
compile/link classpaths retain their JS/Native runtime. The dependency classpath
uses SBT 2's public fileConverter to resolve virtual-file references. Compile
and Test macro compilation need their respective dependency classpaths.

Corrected minimal JS/Native builds both return actual exit 0 for ordinary and
instrumented compilation. An independent audit confirms the macro helper's
class still contains the Invoker call and its measurement file exists. No
package exclusion is needed. Captures:
`/srv/nvme/tmp/izumi-impl/3-scoverage-macro-host-runtime-public/` and
`/srv/nvme/tmp/izumi-impl/3-scoverage-macro-host-runtime-public-second/`.
Corrected independent audit SHA256:
`f97aa6feef3985cbb7ea95c552f119b4a042c34b8239422ac2dbfa5ae8618e48`.
The repository control batch remains a separate required check.

## Native extern declaration reproduction

A separate Scala Native project with the same pinned versions and corrected
compiler dependency coordinates defines:

```scala
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
@extern object NativeWitness {
  def getentropy(buffer: Ptr[Byte], size: CSize): CInt = extern
}
object NativeCaller {
  def main(args: Array[String]): Unit = {
    val buffer = stackalloc[Byte]()
    require(NativeWitness.getentropy(buffer, 1.toUSize) == 0)
    println("NATIVE_EXTERN_RUNTIME_OK")
  }
}
```

`sbt --server -batch compile run coverage compile` first compiles and calls
getentropy successfully, then exits 1 at the extern declaration with
`methods in extern objects must have extern body`. Adding
`coverageExcludedPackages := "NativeWitness.*"` makes that sequence and a final
instrumented `run` return 0. The declaration has no Scala body to instrument;
the Scala wrapper remains instrumented. The independent audit verifies its
Invoker call, absence of that call from the extern class, and both physical
entropy-call markers. It does not establish C implementation coverage.

Captures: `/srv/nvme/tmp/izumi-impl/3-scoverage-native-extern-public-second/`.
The first prototype failed its ordinary baseline due to Scala 2 import syntax,
and the second prototype controller initially could not copy an unprepared
corrected template. Those infrastructure failures are not passing evidence.
The preserved fail-first child reproduces the extern diagnostic; the completed
corrected child and independent audit validate the stated correction.
