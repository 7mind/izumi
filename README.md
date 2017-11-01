Build options
-------------

1. `build.coursier` - enable Coursier resolver

Releases
--------

1. Use `release release-version 1.0.99 next-version 1.2.0-SNAPSHOT` or `release release-version 1.0.99 next-version 1.2.0-SNAPSHOT skip-tests`  
2. `git checkout v1.0.99`
3. `sbt publishSigned sonatypeRelease` 
