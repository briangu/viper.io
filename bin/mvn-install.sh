#!/bin/bash
#echo "installing jmxri 1.2.1"
mvn install:install-file -Dfile=lib/jmxri-1.2.1.jar -DgroupId=com.sun.jmx -DartifactId=jmxri -Dversion=1.2.1 -Dpackaging=jar
#echo "installing jmxtools 1.2.1"
mvn install:install-file -Dfile=lib/jmxtools-1.2.1.jar -DgroupId=com.sun.jdmk -DartifactId=jmxtools -Dversion=1.2.1 -Dpackaging=jar
#echo "installing kafka 0.0.6-SNAPSHOT"
mvn install:install-file -Dfile=lib/kafka-0.7.jar -DgroupId=com.sna-projects.kafka -DartifactId=kafka -Dversion=0.7 -Dpackaging=jar

