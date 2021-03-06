/*
 * (C) Copyright 2015-2019 Nuxeo (http://nuxeo.com/) and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Delbosc Benoit
 */
package org.nuxeo.drive.bench

import io.gatling.core.Predef._

object ServerFeeder {

  def run = (thinkTime: Integer) => {
    val filename = "file_${user}"
    val halfSleep = (thinkTime.floatValue() / 2).ceil.toInt
    group("Server Feeder") {
      feed(Feeders.users)
        .exec(
          Actions.createFileDocument(Constants.GAT_WS_PATH + "/" + Constants.GAT_FOLDER_NAME, filename)
        ).pause(halfSleep)
        .exec(
          Actions.createFileDocument(Constants.GAT_WS_PATH + "/" + Constants.GAT_USER_FOLDER_NAME, filename)
        ).pause(halfSleep)
        .repeat(2, "count") {
        exec(
          Actions.updateFileDocument(Constants.GAT_WS_PATH + "/" + Constants.GAT_FOLDER_NAME, filename)
        ).pause(halfSleep)
          .exec(
            Actions.updateFileDocument(Constants.GAT_WS_PATH + "/" + Constants.GAT_USER_FOLDER_NAME, filename)
          ).pause(halfSleep)
      }.exec(
          Actions.deleteFileDocument(Constants.GAT_WS_PATH + "/" + Constants.GAT_FOLDER_NAME + "/" + filename)
        ).pause(halfSleep)
        .exec(
          Actions.deleteFileDocument(Constants.GAT_WS_PATH + "/" + Constants.GAT_USER_FOLDER_NAME + "/" + filename)
        )
    }
  }

}
