#!/usr/bin/env bash

sdk_path=$1
ndk_version=$2

echo "Android SDK path: $sdk_path";
echo "NDK version: $ndk_version"

yes | $sdk_path/sdkmanager --install "$ndk_version"