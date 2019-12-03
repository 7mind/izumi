Debugging
=========

### Pretty-printing plans

You can print the `plan` to get detailed info on what will happen during instantiation. The printout includes source
and line numbers so your IDE can show you where the binding was defined!

```scala
val plan = Injector().plan(module)

System.err.println(plan)
```

![print-test-plan](media/print-test-plan.png)

You can also query a plan to see the dependencies and reverse dependencies of a specific class and their order of instantiation:

```scala
// Print dependencies
System.err.println(plan.topology.dependencies.tree(DIKey.get[Circular1]))
// Print reverse dependencies
System.err.println(plan.topology.dependees.tree(DIKey.get[Circular1]))
```

![print-dependencies](media/print-dependencies.png)

The printer highlights circular dependencies.

To debug macros used by `distage` you may turn on various java Properties:

```bash
sbt -Dizumi.debug.macro.rtti=true compile # fundamentals-reflection & LightTypeTag macros
sbt -Dizumi.debug.macro.distage.constructors=true compile # izumi.distage.constructors.* macros
sbt -Dizumi.debug.macro.distage.providermagnet=true compile # ProviderMagnet macro
```

### Graphviz rendering

Add `GraphDumpBootstrapModule` to your `Injector`'s configuration to enable dumping of graphviz files with a graphical representation of the `Plan`.

```scala
val injector = Injector(new GraphDumpBootstrapModule())
```

Data will be saved dumped to `./target/plan-last-full.gv` and `./target/plan-last-nogc.gv` in current working directory. 

You'll need a `GraphViz` installation to render these files into a viewable PNG images:

```bash
dot -Tpng target/plan-last-nogc.gv -o out.png
```

![plan-graph](media/plan-graph.png)
