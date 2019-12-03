distage-framework
=======================

### Roles

@@@ warning { title='TODO' }
Sorry, this page is not ready yet
@@@

"Roles" are a pattern of multi-tenant applications, in which multiple separate microservices all reside within a single `.jar`.
This strategy helps cut down development, maintenance and operations costs associated with maintaining fully separate code bases and binaries.
Apps are chosen via command-line parameters: `./launcher app1 app2 app3`. If you're not launching all apps
hosted by the launcher at the same time, the redundant components from unlaunched apps will be @ref[garbage collected](other-features.md#garbage-collection)
and won't be started.

consult slides [Roles: a viable alternative to Microservices](https://github.com/7mind/slides/blob/master/02-roles/target/roles.pdf)
for more details.

`distage-framework` module hosts the current experimental Roles API:

@@@vars

```scala
libraryDependencies += "io.7mind.izumi" %% "distage-framework" % "$izumi.version$"
```

@@@
