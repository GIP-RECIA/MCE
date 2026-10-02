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

profile_prod=prod
build_arguments='-Dmaven.test.skip=true -Darguments="-DskipTests"'

package() {
  echo "--- Package ---"
  ./mvnw clean package -P${profile_prod} ${build_arguments}
}

snapshot() {
  echo "--- Snapshot ---"
  ./mvnw clean package deploy -P${profile_prod} ${build_arguments}
}

release() {
  echo "--- Release ---"
  ./mvnw clean package release:prepare release:perform -P${profile_prod} ${build_arguments}
}

if [[ $1 != "" ]]; then
  choice="$1"
else
  echo "0 -> package"
  echo "1 -> snapshot"
  echo "2 -> release"
  echo ""

  read -r -p "Action: " choice
fi

case ${choice} in

0) package ;;
1) snapshot ;;
2) release ;;
*) echo "Unknown choice" ;;

esac
