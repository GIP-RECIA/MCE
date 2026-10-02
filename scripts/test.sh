# Copyright (C) 2023 GIP-RECIA, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

dockerRun() {
  docker run --rm -t -d --name mariadb -v \
    --health-cmd="mysqladmin -uroot -proot ping" --health-interval=10s --health-timeout=10s --health-retries=10 \
    -p 3306:3306/tcp \
    -e "TZ=Europe/Paris" \
    -e "MYSQL_USER=root" \
    -e "MYSQL_ROOT_PASSWORD=root" \
    -e "MYSQL_DATABASE=glc" \
    -e "MYSQL_DEFAULT_STORAGE_ENGINE=InnoDB" \
    -e "MYSQL_CHARACTER_SET_SERVER=utf8mb4" \
    -e "MYSQL_COLLATION_SERVER=utf8mb4_unicode_520_ci" \
    -e "MYSQL_INNODB_BUFFER_POOL_SIZE=2G" \
    -e "MYSQL_INNODB_DEFAULT_ROW_FORMAT=dynamic" \
    -e "MYSQL_INNODB_DATA_FILE_PATH=ibdata1:100M:autoextend:max:10G" \
    -e "MYSQL_INNODB_FLUSH_LOG_AT_TRX_COMMIT=1" \
    -e "MYSQL_INNODB_LOG_BUFFER_SIZE=64M" \
    -e "MYSQL_INNODB_LOG_FILE_SIZE=256M" \
    -e "MYSQL_INNODB_STRICT_MODE=ON" \
    -e "MYSQL_LOWER_CASE_TABLE_NAMES=1" \
    -e "MYSQL_MAX_CONNECT_ERRORS=100" \
    -e "MYSQL_MAX_CONNECTIONS=1000" \
    -e "MYSQL_QUERY_CACHE_LIMIT=10M" \
    -e "MYSQL_QUERY_CACHE_SIZE=0" \
    -e "MYSQL_QUERY_CACHE_TYPE=OFF" \
    wodby/mariadb:10.3 &
  sleep 30
  mysql -h127.0.0.1 -uroot -proot < ./src/test/resources/glc.sql
}

dockerStop() {
  docker stop mariadb
}

tests() {
  ./mvnw -B clean package -Ptest
  echo "Find results at: file://${PWD}/target/site/jacoco/index.html"
}

if [[ $1 != "" ]]; then
  choice="$1"
else
  echo "1 -> tests with docker"
  echo "2 -> run docker"
  echo "3 -> tests only"
  echo "4 -> stop docker"
  echo ""

  read -r -p "Action: " choice
fi

case ${choice} in

1)
  dockerRun
  tests
  dockerStop
  ;;
2) dockerRun ;;
3) tests ;;
4) dockerStop ;;
*) echo "Unknown choice" ;;

esac
