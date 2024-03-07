#!/bin/sh
cs launch com.lihaoyi:ammonite_2.13.12:2.5.11 --fork -M ammonite.Main -- sbtgen.sc $*
exit
!#
import $file.project.Deps, Deps._

@main
def entrypoint(args: String*) = Izumi.entrypoint(args)
