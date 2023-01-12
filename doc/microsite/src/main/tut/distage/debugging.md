# Debugging

@@toc { depth=2 }

### Testing Plans

Use `Injector#assert` method to test whether the plan will execute correctly when passed to `Injector#produce`.

```scala mdoc:reset:to-string
import distage.{DIKey, Roots, ModuleDef, Injector}

class A(b: B)
class B

def badModule = new ModuleDef {
  make[A]
  make[B].fromEffect(zio.Task { ??? })
}
```

```scala mdoc:crash:to-string
// the effect types are mismatched - `badModule` uses `zio.Task`, but we expect `cats.effect.IO`

Injector[cats.effect.IO]().assert(badModule, Roots.target[A])
```

```scala mdoc:to-string
def goodModule = new ModuleDef {
  make[A]
  make[B].fromEffect(cats.effect.IO(new B))
}
```

```scala mdoc:to-string
// the effect types in `goodModule` and here match now

Injector[cats.effect.IO]().assert(goodModule, Roots.target[A])
```

### Pretty-printing plans

You can print the output of `plan.render()` to get detailed info on what will happen during instantiation. The printout includes source
and line numbers so your IDE can show you where the binding was defined!

```scala mdoc:to-string
import distage.PlannerInput

val plan = Injector().plan(goodModule, Roots.target[A]).getOrThrow()

println(plan.render())
```

![print-test-plan](media/print-test-plan.png)

You can also query a plan to see the dependencies and reverse dependencies of a specific class and their order of instantiation:

```scala mdoc:to-string
// Print dependencies
println(plan.renderDeps(DIKey[A]))

// Print reverse dependencies
println(plan.renderDependees(DIKey[B]))
```

The printer highlights circular dependencies:

![print-dependencies](media/print-dependencies.png)

To debug macros used by `distage` you may use the following Java Properties:

```bash
# izumi-reflect macros
-Dizumi.debug.macro.rtti=true

# izumi.distage.constructors.* macros
-Dizumi.debug.macro.distage.constructors=true

# Functoid macro
-Dizumi.debug.macro.distage.functoid=true
```

### Graphviz rendering

Add `GraphDumpBootstrapModule` to your `Injector`'s configuration to enable writing GraphViz files with a graphical representation of `distage.Plan`. Data will be saved to `./target/plan-last-full.gv` and `./target/plan-last-nogc.gv` in the current working directory.

```scala mdoc:reset:to-string
import distage.{GraphDumpBootstrapModule, Injector}

Injector(GraphDumpBootstrapModule)
```

You'll need a `GraphViz` installation to render these files into a viewable PNG images:

```bash
dot -Tpng target/plan-last-nogc.gv -o out.png
```

![plan-graph](media/plan-graph.png)

#### Command-line activation

You may activate GraphViz dump for a `distage-framework` @ref[Role-based application](distage-framework.md#roles) by passing a `--debug-dump-graph` option:

```
./launcher --debug-dump-graph :myrole
```

#### Testkit activation

You may activate GraphViz dump in `distage-testkit` tests by setting `PlanningOptions(addGraphVizDump = true)` in `config`:

```scala mdoc:reset
import izumi.distage.testkit.scalatest.Spec2
import izumi.distage.testkit.TestConfig
import izumi.distage.framework.config.PlanningOptions

final class MyTest extends Spec2[zio.IO] {
  override def config: TestConfig = super.config.copy(
    planningOptions = PlanningOptions(
      addGraphVizDump = true,
    )
  )
}
```

##### Launcher activation

PlanningOptions are also modifiable in `distage-framework` applications:

```scala mdoc:reset
import distage.{Module, ModuleDef}
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.roles.RoleAppMain
import zio.IO

abstract class MyRoleLauncher extends RoleAppMain.LauncherBIO2[IO] {
  override protected def roleAppBootOverrides(argv: RoleAppMain.ArgV): Module = new ModuleDef {
    make[PlanningOptions].from(PlanningOptions(addGraphVizDump = true))
  }
}
```
