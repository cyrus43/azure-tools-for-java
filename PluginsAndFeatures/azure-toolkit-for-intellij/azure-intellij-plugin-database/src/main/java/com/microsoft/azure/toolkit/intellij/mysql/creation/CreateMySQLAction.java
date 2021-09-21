/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.mysql.creation;

import com.intellij.openapi.project.Project;
import com.microsoft.azure.toolkit.intellij.common.Draft;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.mysql.AzureMySql;
import com.microsoft.azure.toolkit.lib.mysql.MySqlServer;
import com.microsoft.azure.toolkit.lib.mysql.model.MySqlServerConfig;
import com.microsoft.azure.toolkit.lib.resource.AzureGroup;

public class CreateMySQLAction {

    public static void createAzureMySQL(Project project) {
        final MySQLCreationDialog dialog = new MySQLCreationDialog(project);
        dialog.setOkActionListener((config) -> {
            dialog.close();
            createMySQL(config);
        });
        dialog.show();
    }

    @AzureOperation(
            name = "mysql|server.create.task",
            params = {
                    "config.getServerName()",
                    "config.getSubscription().getName()"
            },
            type = AzureOperation.Type.SERVICE
    )
    public static void createMySQL(final AzureMySQLConfig config) {
        final String subscriptionId = config.getSubscription().getId();
        // create resource group if necessary.
        if (config.getResourceGroup() instanceof Draft) {
            try {
                Azure.az(AzureGroup.class).get(subscriptionId, config.getResourceGroup().getName());
            } catch (final Throwable ex) {
                Azure.az(AzureGroup.class).subscription(subscriptionId).create(config.getResourceGroup().getName(), config.getRegion().getName());
            }
            config.setResourceGroup(Azure.az(AzureGroup.class).get(subscriptionId, config.getResourceGroup().getName()));
        }
        // create mysql server
        final MySqlServer server = Azure.az(AzureMySql.class).subscription(subscriptionId)
                .create(MySqlServerConfig.builder()
                        .subscription(config.getSubscription())
                        .resourceGroup(config.getResourceGroup())
                        .region(config.getRegion())
                        .name(config.getServerName())
                        .version(config.getVersion())
                        .administratorLoginName(config.getAdminUsername())
                        .administratorLoginPassword(String.valueOf(config.getPassword()))
                        .build()).commit();
        // update access from azure services
        if (config.isAllowAccessFromAzureServices()) {
            server.firewallRules().enableAzureAccessRule();
        }
        // update access from local machine
        if (config.isAllowAccessFromLocalMachine()) {
            server.firewallRules().enableLocalMachineAccessRule(server.getPublicIpForLocalMachine());
        }
    }
}
