#!/bin/bash -xe

export THISDIR="$( cd "$(dirname "$0")" ; pwd -P )"

pushd .
cd $THISDIR

cat irt.nuspec | sed 's/0.0.1-UNSET/'${IZUMI_VERSION}'/g' > irt.tmp.nuspec

nuget pack irt.tmp.nuspec
rm irt.tmp.nuspec

nuget setapikey $NUGET_TOKEN

for TRG in $(find . -name '*.nupkg' -type f -print)
do
    nuget push $TRG -Source https://www.nuget.org
done

popd
