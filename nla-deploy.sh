#!/bin/bash
if [ -z "$TNH_INDEX_PATH" ]; then
  echo TNH_INDEX_PATH environment variable must be set to the location of the index
  exit 1
fi
xmlstarlet ed --inplace -P -u '//init-param[param-name="index"]/param-value' -v "$TNH_INDEX_PATH" ./src/main/webapp/WEB-INF/web.xml
mvn package
unzip -d $1/ROOT target/*.war
