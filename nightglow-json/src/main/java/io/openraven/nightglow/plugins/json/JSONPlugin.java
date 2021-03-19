/*
 * Copyright 2021 Open Raven Inc
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
 */

package io.openraven.nightglow.plugins.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.openraven.nightglow.api.NGEnvelope;
import io.openraven.nightglow.api.TerminalPlugin;
import org.slf4j.Logger;

import java.io.IOException;

public class JSONPlugin implements TerminalPlugin<Void> {

  private static final String ID = "nightglow.json";
  private static final ObjectMapper MAPPER = new ObjectMapper()
    .enable(SerializationFeature.INDENT_OUTPUT)
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    .findAndRegisterModules();

  private Logger logger;

  @Override
  public void accept(NGEnvelope ngEnvelope) {
    try {
//      final var obj = MAPPER.readTree(ngEnvelope.getContents());
      var output = MAPPER.writeValueAsString(ngEnvelope);  // Jackson auto-closes streams by default and we don't wish to close stdout.
      System.out.println(output);
    } catch (IOException ex) {
      logger.warn("Couldn't process envelope contents", ex);
    }
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public void init(Void unused, Logger logger) {
    this.logger = logger;
  }

  @Override
  public Class<Void> configType() {
    return null;
  }
}
