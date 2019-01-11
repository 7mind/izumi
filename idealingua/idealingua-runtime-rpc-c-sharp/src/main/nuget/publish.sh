#!/bin/bash -xe

export THISDIR="$( cd "$(dirname "$0")" ; pwd -P )"

pushd .
cd $THISDIR

rm -rf *.nupkg

NUSPEC=irt.tmp.nuspec
cat irt.nuspec | sed 's/0.0.1-UNSET/'${IZUMI_VERSION}'/g' > $NUSPEC
cat $NUSPEC
nuget pack $NUSPEC
rm $NUSPEC

nuget setapikey $NUGET_TOKEN

for TRG in $(find . -name '*.nupkg' -type f -print)
do
    nuget push $TRG -Source https://www.nuget.org -Verbosity detailed
done

popd
