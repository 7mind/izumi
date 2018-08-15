Build notes
===========

Prerequisites
-------------

On mac:

```
brew tap caskroom/versions
brew update
brew install homebrew/cask/java sbt npm go
npm i -g typescript
```

Build options
-------------

1. `build.publish.overwrite` - enable stable artifact reuploading
2. `build.coursier.use` - enable Coursier resolver (not available in dependent projects)
3. `build.coursier.version` - override Coursier version (not available in dependent projects)

SBT plugin debugging
--------------------

Once you have the project published locally you may open projects from `sbt/sbt-tests` in IDEA.

So, in case you wish to work with IDL animal model project you should:

1. Perform `sbt publishLocal` from project root
2. `cd` into [sbt/sbt-tests/src/sbt-test](sbt/sbt-tests/src/sbt-test/) or open it as a project
3. Play 
