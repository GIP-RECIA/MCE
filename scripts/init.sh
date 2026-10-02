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

source ~/.nvm/nvm.sh
source ~/.sdkman/bin/sdkman-init.sh

# Frontend
nvm install
npm i -g yarn
yarn
yarn exec dotenv-checker

# Backend
if ! sdk env; then
  sdk env install
fi
if [ ! -f "src/main/resources/config/application-dev.yml" ]; then
  cp -u src/main/resources/config/application-dev.example.yml src/main/resources/config/application-dev.yml
fi
