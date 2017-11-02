Build options
-------------

1. `build.publish.overwrite` - enable stable artifact reuploading
2. `build.coursier.use` - enable Coursier resolver (not available in dependent projects)
3. `build.coursier.version` - override Coursier version (not available in dependent projects)

Releases
--------

1. Use `release release-version 1.0.99 next-version 1.2.0-SNAPSHOT` or `release release-version 1.0.99 next-version 1.2.0-SNAPSHOT skip-tests`  
2. `git checkout v1.0.99`
3. `sbt publishSigned sonatypeRelease` 
