#!/bin/bash
echo "Installing RPC Framework JARs to local Maven repository..."

echo "Installing rpc-common..."
mvn install:install-file -Dfile=lib/rpc-common-1.0.0.jar -DgroupId=com.rpc -DartifactId=rpc-common -Dversion=1.0.0 -Dpackaging=jar

echo "Installing rpc-core..."
mvn install:install-file -Dfile=lib/rpc-core-1.0.0.jar -DgroupId=com.rpc -DartifactId=rpc-core -Dversion=1.0.0 -Dpackaging=jar

echo "Installing rpc-server..."
mvn install:install-file -Dfile=lib/rpc-server-1.0.0.jar -DgroupId=com.rpc -DartifactId=rpc-server -Dversion=1.0.0 -Dpackaging=jar

echo "Installing rpc-client..."
mvn install:install-file -Dfile=lib/rpc-client-1.0.0.jar -DgroupId=com.rpc -DartifactId=rpc-client -Dversion=1.0.0 -Dpackaging=jar

echo "Installing rpc-registry..."
mvn install:install-file -Dfile=lib/rpc-registry-1.0.0.jar -DgroupId=com.rpc -DartifactId=rpc-registry -Dversion=1.0.0 -Dpackaging=jar

echo ""
echo "Installation completed! You can now use the RPC Framework in your Maven projects."
echo "Add the following dependencies to your pom.xml:"
echo ""
echo "<dependency>"
echo "    <groupId>com.rpc</groupId>"
echo "    <artifactId>rpc-server</artifactId>"
echo "    <version>1.0.0</version>"
echo "</dependency>"
echo ""
echo "<dependency>"
echo "    <groupId>com.rpc</groupId>"
echo "    <artifactId>rpc-client</artifactId>"
echo "    <version>1.0.0</version>"
echo "</dependency>"
echo "" 