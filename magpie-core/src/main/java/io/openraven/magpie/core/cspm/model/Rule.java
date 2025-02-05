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
package io.openraven.magpie.core.cspm.model;

public class Rule {
  private final String type = "asset";
  private String id;
  private String ruleId;
  private String ruleName;
  private String description;
  private Severity severity;
  private boolean enabled = true;
  private boolean manualControl = false;
  private String sql;
  private String eval;
  private String remediationDocURLs;
  private String version;
  private String fileName;

  public Rule() {
  }

  public Rule(String id) {
    this.id = id;
  }

  public boolean isManualControl() {
    return manualControl;
  }

  public void setManualControl(boolean manualControl) {
    this.manualControl = manualControl;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getRuleId() {
    return ruleId;
  }

  public void setRuleId(String ruleId) {
    this.ruleId = ruleId;
  }

  public String getRuleName() {
    return ruleName;
  }

  public void setRuleName(String ruleName) {
    this.ruleName = ruleName;
  }

  public String getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Severity getSeverity() {
    return severity;
  }

  public void setSeverity(Severity severity) {
    this.severity = severity;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getSql() {
    return sql;
  }

  public void setSql(String sql) {
    this.sql = sql;
  }

  public String getRemediationDocURLs() {
    return remediationDocURLs;
  }

  public void setRemediationDocURLs(String remediationDocURLs) {
    this.remediationDocURLs = remediationDocURLs;
  }

  public String getEval() {
    return eval;
  }

  public void setEval(String eval) {
    this.eval = eval;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

}
