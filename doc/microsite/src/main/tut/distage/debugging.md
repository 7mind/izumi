Debugging
=========

### Testing Plans

Use `OrderedPlan#assertImportsResolvedOrThrow` method to test whether all dependencies in a given plan are present and the
plan will execute correctly when passed to `Injector#produce`.

```scala mdoc:reset
import distage.{DIKey, GCMode, ModuleDef, Injector}

class A(b: B)
class B

val badModule = new ModuleDef {
  make[A]
}

val badPlan = Injector().plan(badModule, GCMode.NoGC)
```

```scala mdoc:crash
badPlan.assertImportsResolvedOrThrow
```

```scala mdoc
val goodModule = new ModuleDef {
  make[A]
  make[B]
}

val plan = Injector().plan(goodModule, GCMode.NoGC)

plan.assertImportsResolvedOrThrow
```

### Pretty-printing plans

You can print the output of `plan.render()` to get detailed info on what will happen during instantiation. The printout includes source
and line numbers so your IDE can show you where the binding was defined!

```scala mdoc
println(plan.render())
```

![print-test-plan](media/print-test-plan.png)

You can also query a plan to see the dependencies and reverse dependencies of a specific class and their order of instantiation:

```scala mdoc
// Print dependencies
println(plan.topology.dependencies.tree(DIKey.get[A]))

// Print reverse dependencies
println(plan.topology.dependees.tree(DIKey.get[B]))
```

The printer highlights circular dependencies:

![print-dependencies](media/print-dependencies.png)

To debug macros used by `distage` you may use the following Java Properties:

```bash
sbt -Dizumi.debug.macro.rtti=true compile # fundamentals-reflection & LightTypeTag macros
sbt -Dizumi.debug.macro.distage.constructors=true compile # izumi.distage.constructors.* macros
sbt -Dizumi.debug.macro.distage.providermagnet=true compile # ProviderMagnet macro
```

### Graphviz rendering

Add `GraphDumpBootstrapModule` to your `Injector`'s configuration to enable dumping of graphviz files with a graphical representation of the `Plan`.

```scala mdoc
import distage.GraphDumpBootstrapModule

val injector = Injector(GraphDumpBootstrapModule())
```

Data will be saved dumped to `./target/plan-last-full.gv` and `./target/plan-last-nogc.gv` in current working directory. 

You'll need a `GraphViz` installation to render these files into a viewable PNG images:

```bash
dot -Tpng target/plan-last-nogc.gv -o out.png
```

![plan-graph](media/plan-graph.png)
