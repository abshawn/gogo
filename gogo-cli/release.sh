#!/usr/bin/env bash
# 发布脚本

# 检查参数
if [[ "$#" -ne 1 ]]; then
    echo "usage: "$0" <version>"
    exit 1
fi

# 检查main.go文件
if [[ ! -f "main.go" ]]; then
    echo "file main.go does not exists"
    exit 2
fi

# 版本号
VERSION=$1
# 可执行文件前缀
EXECUTABLE_PREFIX="gogo-cli"

# 创建并切换文件夹
WORKDIR="release_"$VERSION
mkdir $WORKDIR
cd $WORKDIR

# 操作系统数组
OS=("linux" "linux" "linux" "linux" "darwin" "darwin" "windows" "windows")
# 架构数组
ARCH=("386" "amd64" "arm" "arm64" "386" "amd64" "386" "amd64")
# 可执行文件后缀
EXECUTABLE_POSTFIX=("Linux-32bit" "Linux-64bit" "Linux-ARM" "Linux-ARM64" "macOS-32bit" "macOS-64bit" "Windows-32bit.exe" "Windows-64bit.exe")

# 最大的索引
MAX_INDEX=$(expr ${#OS[@]} - 1)

for i in $(seq 0 $MAX_INDEX); 
do
    EXECUTABLE=$EXECUTABLE_PREFIX"_"$VERSION"_"${EXECUTABLE_POSTFIX[$i]}
    GOOS=${OS[$i]} GOARCH=${ARCH[$i]} go build -o $EXECUTABLE ../main.go && \
    sha1sum $EXECUTABLE > $EXECUTABLE".sha1sum"
done
