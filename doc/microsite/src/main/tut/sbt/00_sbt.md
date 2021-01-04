---
out: index.html
---
SBT Toolkit
===========

@@@ warning { title='MOVED' }
Moved to https://github.com/7mind/sbtgen/
@@@

Izumi provides you a bunch of sbt plugins allowing you to significantly reduce size and increase clarity of huge multi-module builds.

To start using Izumi plugins add the following into your `project/plugins.sbt`:

@@@vars
```scala
// This is Izumi Bill of Materials, see below
addSbtPlugin("io.7mind.izumi" % "sbt-izumi-deps" % "$izumi.version$")

// Izumi SBT plugins
addSbtPlugin("io.7mind.izumi.sbt" % "sbt-izumi" % "0.0.57")
```
@@@

We provide you the following kinds of plugins:

1. *Global plugins*: they add some helper sbt tasks, enabled automatically,
2. *Environmental plugins*: they change some aspects of sbt behavior in an opinionated way, intended to be enabled in the root project,
3. *Presets* - opinionated sets of environmental plugins,
4. *Optional plugins*: they provide some convenience sbt tasks, intended to be enabled manually, per-project.

Important notes:

1. Please keep in mind no plugins are being enabled automatically,
2. The rest of Izumi plugins are optional. You may use a preset to activate them or combine them manually as you wish (using settings groups),
3. Some plugins can't be enabled globally due to sbt limitations – they have to be enabled per-project. You should use Settings Groups in order to enable them.

### Global plugins

Plugin                             | Description                                        |
----------------------------------------------------------------------------------------|
IzumiImportsPlugin                 | Makes all the Izumi imports visible by default     |

### Environmental plugins

Plugin                             | Description                                        |
----------------------------------------------------------------------------------------|
IzumiBuildManifestPlugin           | Adds build info into jar manifests                 |
IzumiConvenienceTasksPlugin        | Devops/workflow helpers                            |
IzumiDslPlugin                     | Compact build definitions                          |
IzumiGitStampPlugin                | Adds GIT status into jar manifests                 |
IzumiPropertiesPlugin              | Convenience helpers for `sys.props`                |
IzumiResolverPlugin                | Better defaults for artifact resolution            |
IzumiInheritedTestScopesPlugin     | Convenience helpers for test scope inheritance     |

### Presets

Plugin                             | Description                                        |
----------------------------------------------------------------------------------------|
IzumiEnvironment                   | All the environmental plugins except of GIT one    |
IzumiGitEnvironment                | All the environmental plugins with GIT one         |

### Optional plugins

Plugin                             | Description                                        |
----------------------------------------------------------------------------------------|
IzumiExposedTestScopesPlugin       | Maintains test scope separation                    |
IzumiCompilerOptionsPlugin         | Some linting/optimization presets for scalac/javac |
IzumiFetchPlugin                   | Allows you to transitively download artifacts from remote repositories|
IzumiPublishingPlugin              | Some convenience helpers and improvements for artifact publishing |
IzumiBuildInfoPlugin               | Generates sbt-compatible BOMs – [Bills of Materials](#bills-of-materials) |

### Installation
To activate all the plugins add the following statements into your root project:

```scala
enablePlugins(IzumiGitEnvironmentPlugin)
```

If you don't use `git` in your project, use this preset instead:

```scala
enablePlugins(IzumiEnvironmentPlugin)
```

To activate Comp

```scala
val GlobalSettings = new DefaultGlobalSettingsGroup {
  override val plugins = Set(IzumiCompilerOptionsPlugin, IzumiExposedTestScopesPlugin)
}
```

Inherited Test Scopes
---------------------

### Test Scope Inheritance

`IzumiScopesPlugin` extends SBT `Project` with several implicit methods:

- `testOnlyRef` - provides you a project reference which is equivalent to `% "test"` dependency,
- `dependsSeq` and `depends` - allow you to add dependencies to your project the way test scopes of your dependencies are visible within test scopes of your project. So, essentially when you use these methods your dependencies are added into your projects with the following qualifier: `"test->compile,test;it->compile,test,it"`

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
- `TestUtil` is annotated with `@ExposedTestScope`,

in that case `TestUtil` will be visible in `App`'s test scope, but `TestSuite` will not be visible.

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

- Intellij IDEA doesn't support overriden classpaths so when you run your tests under IDEA the whole test scopes are visible in dependencies,
- At the moment the implementation of `@ExposedTestScope` (substring presence check) is imperfect and has to be improved,
- **Transitive dependencies are not checked**, so in case you expose a class but do not expose it's dependencies your build will
  work under IDEA, but you will a classloading exception under sbt.
  This is going to be [improved](https://github.com/7mind/izumi/issues/6) in the future.

### Test Scope Publishing

The whole content of test scopes is being published by default with `test` qualifier.
Test scope separation has no effect on test scope publishing.

Settings DSL
------------

`IzumiDslPlugin` comes with a DSL intended to simplify definition of complex project layouts.

To activate the plugin add the following statement into your root project:

```scala
enablePlugins(IzumiDslPlugin)
```

### Simplified Identifiers

DSL provides syntax to simplify project definitions. A definition such as this:

```scala
lazy val petstoreApp = In("app").as.module
```

expands to

    lazy val petstoreApp = project.in("app/petstore-app")

You can attach settings and dependencies to the `In` part, that way you can apply common settings to all the projects in a directory:

```scala
val ApiSettings = new SettingsGroup {
  override val plugins = Set(IdealinguaPlugin)
}

lazy val inApi = In("api").settings(ApiSettings)

lazy val petstoreApi = inApi.as.module
lazy val todomvcApi = inApi.as.module
```

### Setting Groups

#### Global Setting Group

### Automatic Aggregation

When you use `.as.project` or `.as.module` syntax to define a project, that project is stored in a global singleton.

You may use `transitiveAggregate` or `transitiveAggregateSeq` methods instead of standard `aggregate`,
in that case all the transitive dependencies of the projects provided will be also added into aggregation list. This allows you to simplify your definitions by avoiding specifing all the modules in `.aggregate`.

In case you don't want your project to be recorded, you shoud use `.as.just` syntax.

@@@ warning { title='Important note: sbt is lazy!' }
We just store project reference in a singleton list, we don't analyze dependency graph. Though sbt relies on `lazy val` declarations.
*So, in fact the singleton initializes at the moment you invoke aggregation.*
In case you don't add a project into aggregation list such a project still will be initialized by sbt and added into the singleton,
but it would happen *later* than evaluation  of aggregation list so the project will not be aggregated.
@@@

### Aggregation Safety Check

When you invoke `transitiveAggregate` or `transitiveAggregateSeq` on your root project it checks
if the accumulated set of known project is the same as the set of all projects loaded by sbt.

In case module is missing you'll get a warning. This makes it unlikely for you to accidentally forget
to aggregate a module in multi-module project.

Build Descriptors
-----------------

### Bills of Materials

Izumi brings [Bill of Materials](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html) concept to SBT.

#### Izumi BOM

You can import Izumi libraries and transitive dependencies without specifying their version or even _artifact names_. To do that
add `sbt-izumi-deps` SBT plugin:

@@@vars
```scala
val izumi_version = "$izumi.version$"
addSbtPlugin("io.7mind.izumi" % "sbt-izumi-deps" % izumi_version)
```
@@@

You can use it like this:

```scala
libraryDependencies += Izumi.R.distage_core // Import an izumi library
libraryDependencies += IzumiDeps.R.cats_effect // Import a dependency of izumi
libraryDependencies += IzumiDeps.T.scalatest // Import a dependency of izumi in test scope
```

R is for Runtime artifacts and T is for Test scope artifacts

#### Create a BOM for your projects

`sbt-izumi-deps` allows you to a create BOMs for your own projects. Just add the following snippet into your project settings:

```scala
lazy val myProject = (project in file("my-project-bom"))
  .settings(withBuildInfo("com.mycompany.myproject", "MyProjectBOM"))
```

If you use Izumi SBT DSL, you can use the following syntax:

```scala
lazy val inRoot = In(".")
lazy val myProjectBom = inRoot.as.module
  .settings(withBuildInfo("com.mycompany.myproject", "MyProjectBOM"))

```

If you want to include BOMs of you project, you can use the following syntax:

```scala

import com.mycompany.myproject.MyProjectBOM

lazy val myLib = In("lib").as.module
  .settings(
    libraryDependencies ++= Seq(
      MyProjectBOM.R.my_project_bom,     // Runtime artifact
      MyProjectBOM.T.my_project_bom,     // Runtime artifacts for test scope*
      MyProjectBOM.TSR.my_project_bom,   // Test artifacts
      MyProjectBOM.TST.my_project_bom,   // Test artifacts for test scope
    )
  )

```

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
ConvenienceTasksPlugin.Keys.mkJavaDirs             | Also adds `src/*/java` directories into generated stubs |
ConvenienceTasksPlugin.Keys.defaultStubPackage     | Default stub package. By default same as project's `organization` |

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
      IzumiCompilerOptionsPlugin,
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
