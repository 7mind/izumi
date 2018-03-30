Build notes
===========

Build options
-------------

1. `build.publish.overwrite` - enable stable artifact reuploading
2. `build.coursier.use` - enable Coursier resolver (not available in dependent projects)
3. `build.coursier.version` - override Coursier version (not available in dependent projects)

SBT plugin debugging
--------------------

Once you have the project published locally you may open projects from `sbt/sbt-tests` in IDEA.
