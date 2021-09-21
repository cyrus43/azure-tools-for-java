/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.sqlserver;

import com.intellij.openapi.project.Project;
import com.microsoft.azure.toolkit.intellij.database.IntellijDatasourceService;
import com.microsoft.azure.toolkit.lib.database.JdbcUrl;
import com.microsoft.azure.toolkit.lib.sqlserver.SqlServer;
import com.microsoft.azure.toolkit.lib.sqlserver.model.SqlServerEntity;

import javax.annotation.Nonnull;

public class OpenSqlServerByToolsAction {

    public static final String ACTION_NAME = "Open by Database Tools";
    private static final String SQLSERVER_NAME_PATTERN = "Azure SQL Server - %s";
    private static final String SQLSERVER_DEFAULT_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    public static void open(@Nonnull SqlServer server, Project project) {
        final SqlServerEntity entity = server.entity();
        final IntellijDatasourceService.DatasourceProperties properties = IntellijDatasourceService.DatasourceProperties.builder()
                .name(String.format(SQLSERVER_NAME_PATTERN, entity.getName()))
                .driverClassName(SQLSERVER_DEFAULT_DRIVER)
                .url(JdbcUrl.sqlserver(entity.getFullyQualifiedDomainName()).toString())
                .username(entity.getAdministratorLoginName() + "@" + entity.getName())
                .build();
        IntellijDatasourceService.getInstance().openDataSourceManagerDialog(project, properties);
    }

}
