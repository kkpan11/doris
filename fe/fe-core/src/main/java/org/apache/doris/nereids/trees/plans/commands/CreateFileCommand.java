// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.nereids.trees.plans.commands;

import org.apache.doris.analysis.StmtType;
import org.apache.doris.catalog.Env;
import org.apache.doris.common.AnalysisException;
import org.apache.doris.common.ErrorCode;
import org.apache.doris.common.ErrorReport;
import org.apache.doris.mysql.privilege.PrivPredicate;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;
import org.apache.doris.qe.ConnectContext;
import org.apache.doris.qe.StmtExecutor;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Optional;

/** CreateFileCommand */
public class CreateFileCommand extends Command implements ForwardWithSync {
    public static final String PROP_CATALOG_DEFAULT = "DEFAULT";
    private static final String PROP_CATALOG = "catalog";
    private static final String PROP_URL = "url";
    private static final String PROP_MD5 = "md5";
    private static final String PROP_SAVE_CONTENT = "save_content";

    private static final ImmutableSet<String> PROPERTIES_SET = new ImmutableSet.Builder<String>()
            .add(PROP_CATALOG).add(PROP_URL).add(PROP_MD5).build();
    private final String fileName;
    private String dbName;
    private final Map<String, String> properties;

    // analyze properties will fill these.
    // needed item set after analyzed
    private String catalogName;
    private String downloadUrl;
    private String checksum;
    // if saveContent is true, the file content will be saved in FE memory, so the file size will be limited
    // by the configuration. If it is false, only URL will be saved.
    private boolean saveContent = true;

    public CreateFileCommand(String fileName, String dbName, Map<String, String> properties) {
        super(PlanType.CREATE_FILE_COMMAND);
        this.fileName = fileName;
        this.dbName = dbName;
        this.properties = properties;
    }

    private void analyzeProperties() throws Exception {
        Optional<String> optional = properties.keySet().stream().filter(
                entity -> !PROPERTIES_SET.contains(entity)).findFirst();
        if (optional.isPresent()) {
            throw new AnalysisException(optional.get() + " is invalid property");
        }

        catalogName = properties.get(PROP_CATALOG);
        if (Strings.isNullOrEmpty(catalogName)) {
            catalogName = PROP_CATALOG_DEFAULT;
        }

        downloadUrl = properties.get(PROP_URL);
        if (Strings.isNullOrEmpty(downloadUrl)) {
            throw new AnalysisException("download url is missing");
        }

        if (properties.containsKey(PROP_MD5)) {
            checksum = properties.get(PROP_MD5);
        }

        if (properties.containsKey(PROP_SAVE_CONTENT)) {
            throw new AnalysisException("'save_content' property is not supported yet");
        }
    }

    @Override
    public void run(ConnectContext ctx, StmtExecutor executor) throws Exception {
        // check operation privilege
        if (!Env.getCurrentEnv().getAccessManager().checkGlobalPriv(ConnectContext.get(), PrivPredicate.ADMIN)) {
            ErrorReport.reportAnalysisException(ErrorCode.ERR_SPECIFIC_ACCESS_DENIED_ERROR, "ADMIN");
        }

        if (dbName == null) {
            dbName = ctx.getDatabase();
        }

        if (Strings.isNullOrEmpty(dbName)) {
            ErrorReport.reportAnalysisException(ErrorCode.ERR_NO_DB_ERROR);
        }

        if (Strings.isNullOrEmpty(fileName)) {
            throw new AnalysisException("File name is not specified");
        }

        analyzeProperties();
        Env.getCurrentEnv().getSmallFileMgr().createFile(dbName, catalogName, fileName,
                                                    downloadUrl, checksum, saveContent);
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitCreateFileCommand(this, context);
    }

    @Override
    public StmtType stmtType() {
        return StmtType.CREATE;
    }
}
