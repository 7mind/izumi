#!/bin/bash -xe

export THISDIR="$( cd "$(dirname "$0")" ; pwd -P )"

pushd .
cd $THISDIR

cp -R ../main/resources/runtime/typescript/irt .
npm install
tsc
cp package.json dist/
npm install json
./node_modules/json/lib/json.js -I -f dist/package.json -e "this.version=\"${IZUMI_VERSION}\""
cd dist
npm publish --access public || exit 1

popd

