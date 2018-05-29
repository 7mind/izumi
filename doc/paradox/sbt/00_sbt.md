---
out: index.html
---
SBT Toolkit
===========

Add the following into your `project/plugins.sbt`:

@@@vars
```scala
val izumi_version = "$izumi.version$"
addSbtPlugin("com.github.pshirshov.izumi.r2" % "sbt-izumi" % izumi_version)
// This is a Izumi's Bill of Materials, see below
addSbtPlugin("com.github.pshirshov.izumi.r2" % "sbt-izumi-deps" % izumi_version)
```
@@@

To activate all the plugins add the following statements into your root project:

```scala
enablePlugins(IzumiEnvironmentPlugin)
enablePlugins(IzumiDslPlugin)
enablePlugins(GitStampPlugin)
```

Inherited Test Scopes
---------------------

### Test Scope Inheritance

`IzumiScopesPlugin` extends SBT `Project` with several implicit methods:

- `testOnlyRef` - provides you a project reference which is equivalent to `% "test"` dependency,
- `dependsSeq` and `depends` - allow you to add dependencies to your project the way test scopes of your dependencies are visible within test scopes of your project. So, essentially when you use these methods your dependencies are added into your projects with the following qualifier:           `"test->compile,test;it->compile,test,it"`

You need to activate `DefaultItSettingsGroup` on your projects (see "Setting Groups" below) in order to make this working on `it` scope.

Example: 

```scala
lazy val myLibrary = ...

lazy val myProject = (...).depends(myLibrary)
```

So, now you may use classes from the test scope of `myLibrary` within test scope of `myProject`

### Test Scope Separation

`InheritedTestScopesPlugin` works in pair with `IzumiScopesPlugin` and provides you an unique feature:
only the classes you marked with `@ExposedTestScope` are being exposed to dependant artifacts.

So, let's assume that:
- you have two artifacts, `Library` and `App`,
- `App` depends on `Library`,
- In the test scope of `Library` you have a class named `TestSuite`,
- In the test scope of `Library` you have another class named `TestUtil`,
- `TestUtil` is marked with `@ExposedTestScope` anotation,

in that case you may use `@TestUtil` in the test scope of `App`, but `TestSuite` would not be visible.

A diagram:

```text
+-----------------------------------------+     +-----------------------------------------+
| Library                                 |     | App                                     |
|-----------------------------------------|     |-----------------------------------------|
| Main scope                              |     | Main scope                              |
|                                     <---+-----+--                                       |
| UtilityClass                            |     | AppMain                                 |
|-----------------------------------------|     |-----------------------------------------+
| Private Test Scope | Exposed test scope |     | Test scope                              |
|                    |                <---+-----+--                                       |
| TestSuite          | TestUtil           |     | Private Test Scope | Exposed test scope |
+-----------------------------------------+     +-----------------------------------------+
```

Notes:

- IDEA doesn't support overriden classpaths so when you run your tests under IDEA the whole test scopes are visible in dependencies,
- At the moment the implementation of `@ExposedTestScope` (substring presence check) is imperfect and has to be improved,
- **Transitive dependencies are not checked**, so in case you expose a class but didn't expose it's dependencies your build would work under IDEA but you will get a weird and obscure classloading exception running your tests under SNYT. This is going to [improved](https://github.com/pshirshov/izumi-r2/issues/6) in future.

### Test Scope Publishing

The whole content of test scopes is being published by default with `test` qualifier.
Test scope separation has no effect on test scope publishing.

Settings DSL
------------

`IzumiDslPlugin` provides you a DSL inteded to simplify definitions of complex project layouts.

To activate the plugin add the following statement into your root project:

```scala
enablePlugins(IzumiDslPlugin)
```

### Simplified Identifiers

### Setting Groups

#### Global Setting Group

### Automatic Aggregation

When you use `.as.project` or `.as.module` syntax to define a project, that project is stored in a global singleton.

You may use `transitiveAggregate` or `transitiveAggregateSeq` methods instead of standard `aggregate`,
in that case all the transitive dependencies of the projects provided will be also added into aggregation list. This allows you to simplify your definitions by avoiding specifing all the modules in `.aggregate`.

In case you don't want your project to be recorded, you shoud use `.as.just` syntax.

### Aggregation Safety Check

When you invoke `transitiveAggregate` or `transitiveAggregateSeq` on your root project it checks
if the accumulated set of known project is the same as the set of project loaded by sbt.

In case of discrepancies you will get a warning. So in case you use automatic aggregation it's unlikely
that you may accidentally forget to aggregate a module of a multi-module project.

Build Descriptors
-----------------

### Bills of Materials

### Build Manifest entries

### Git Manifest entries

`GitStampPlugin` adds the following values into `MANIFEST.MF` of each `jar` artifact produced:

Manifest Key                 | Description                                                                              |
------------------------------------------------------------------------------------------------------------------------|
`X-Git-Branch`               | The name of branch sbt was invoked on                                                    |
`X-Git-Repo-Is-Clean`        | `true` or `false`, indicates if GIT repo was in clean state when the build was invoked   |
`X-Git-Head-Rev`             | GIT revision on which sbt was invoked                                                    |

To activate the plugin add the following statement into your root project:

```scala
enablePlugins(GitStampPlugin)
```

Convenience Helpers
-------------------

### Stub Generators

`ConvenienceTasksPlugin` provides you the following commands:

- `newModule <module_path/module_name> [package.suffix]`: creates empty project layout at `/module_path/module_name`. Optional `package.suffix` may be specified.
- `newStub <module_path/module_name> [stub_name:default]`: copies `/stubs/${stub_name}` into `/module_path/module_name`. `stub_name` may be omitted, default value is `default`

Setting                                            | Description                                  |
---------------------------------------------------|----------------------------------------------|
ConvenienceTasksPlugin.Keys.mkJavaDirs             | Adds `src/*/java` paths into generated stubs |
ConvenienceTasksPlugin.Keys.defaultStubPackage     | Default stub package `(sbt.Keys.organization in ThisBuild).value`|

### Version Suffixes

`ConvenienceTasksPlugin` provides you command

    addVersionSuffix SUFFIX
    
This command updates project version defined in `version.sbt` with qualifier `SUFFIX-SNAPSHOT`

- Existing qualifiers will be removed. 
- `1.0.0` would become `1.0.0-SUFFIX-SNAPSHOT`
- `1.0.0-SNAPSHOT` would become `1.0.0-SUFFIX-SNAPSHOT`

You may need this command while setting up a build for feature branches and wish to 
avoid situation when different branches publishes artifacts with the same names. 

### `target` preservation

`ConvenienceTasksPlugin` provides you command

    preserveTargets SUFFIX

This command recursively enumerates all the directories named `target` and recursively copies them using name `target.SUFFIX`.

You may need this command while setting up a build where you wish to perform `clean` several times 
but want to preserve intermediate build artifacts stored in `target` directories. The most frequent case is 
coverage build followed by tests and then by production build. In this case you would like to use a command sequence like

    ;clean;coverage;test;coverageOff;coverageReport;preserveTargets coverage;clean;publish

### Directory cleanups

`ConvenienceTasksPlugin` provides you command

    rmDirs target.coverage
    
This command recursively removes all the directories named `target.coverage` across the project.

You may need this command to cleanup preserved target directories, or destroy some build artifacts, like `rmDirs scala-2.11`

### Property Readers

`IzumiPropertiesPlugin` provides you implicit classes allowing you to convert 
Strings into Integers or Booleans exceptions-safe and do the same for system properties: 

```scala
"true".asBoolean(default = false) // true
"junk".asBoolean(default = false) // false
"123".asInt(default = 265) // 123
"junk".asInt(default = 265) // 265
sys.props.asBoolean("my.property", default = false)
sys.props.asInt("my.property", default = 123)
```
You may activate this plugin with the following statement in root project:


### Transitive Artifact Downloader

TODO

Optional settings
-----------------

### Publishing

TODO

#### Publishing settings

TODO

#### Publishing credentials and targets

TODO

### Compiler options

`CompilerOptionsPlugin` provides you some sane compiler option presets (linting, optimizations).

You should explicitly enable this plugin in each project you want to use it. When you want to enable it globally, use a settings group:

```scala
val GlobalSettings = new SettingsGroup {
  override val plugins = Set(
      CompilerOptionsPlugin,
      // ...
  )
  // ...
}
```

### Resolvers

`ResolverPlugin` turns on the following settings which should speedup your dependency resolution:

```scala
    updateOptions := updateOptions
      .value
      .withCachedResolution(true)
      .withGigahorse(true)
```
