/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.mysql;

import com.intellij.openapi.project.Project;
import com.microsoft.azure.toolkit.intellij.database.IntellijDatasourceService;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.database.JdbcUrl;
import com.microsoft.azure.toolkit.lib.mysql.MySqlServer;
import com.microsoft.azure.toolkit.lib.mysql.model.MySqlServerEntity;

import javax.annotation.Nonnull;

public class OpenMySQLByToolsAction {

    public static final String ACTION_NAME = "Open by Database Tools";
    private static final String MYSQL_NAME_PATTERN = "Azure MySQL - %s";
    private static final String MYSQL_DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";

    @AzureOperation(name = "mysql.connect_server", params = {"this.node.getServer().name()"}, type = AzureOperation.Type.ACTION)
    public static void open(@Nonnull MySqlServer server, Project project) {
        final MySqlServerEntity entity = server.entity();
        final IntellijDatasourceService.DatasourceProperties properties = IntellijDatasourceService.DatasourceProperties.builder()
                .name(String.format(MYSQL_NAME_PATTERN, entity.getName()))
                .driverClassName(MYSQL_DEFAULT_DRIVER)
                .url(JdbcUrl.mysql(entity.getFullyQualifiedDomainName()).toString())
                .username(entity.getAdministratorLoginName() + "@" + entity.getName())
                .build();
        IntellijDatasourceService.getInstance().openDataSourceManagerDialog(project, properties);
    }

}
