#! /bin/csh -f

pushd ../limba
bubbles.csh
popd

pushd ../diadbb
ant
popd

ant bubbles









































