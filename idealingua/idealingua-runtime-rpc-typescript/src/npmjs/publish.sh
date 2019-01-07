#!/bin/bash -xe

export THISDIR="$( cd "$(dirname "$0")" ; pwd -P )"

pushd .
cd $THISDIR

cp -R ../main/resources/runtime/typescript/irt .
npm install
tsc
cp package.json dist/
cd dist
npm install -g json
json -I -f package.json -e "this.version=\"${IZUMI_VERSION}\""
npm publish

popd
