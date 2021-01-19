/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.ecm.core.storage;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.TextTemplate;
import org.nuxeo.launcher.commons.DatabaseDriverException;
import org.nuxeo.launcher.config.ConfigurationException;
import org.nuxeo.launcher.config.ConfigurationGenerator;
import org.nuxeo.launcher.config.ConfigurationHolder;
import org.nuxeo.launcher.config.backingservices.BackingChecker;

/**
 * @since 9.2
 * @apiNote reworked in 11.5
 */
public class DBCheck implements BackingChecker {

    private static final Logger log = LogManager.getLogger(DBCheck.class);

    protected static final List<String> DB_EXCLUDE_CHECK_LIST = Arrays.asList("default", "none");

    protected static final String PARAM_DB_DRIVER = "nuxeo.db.driver";

    protected static final String PARAM_DB_JDBC_URL = "nuxeo.db.jdbc.url";

    protected static final String PARAM_DB_HOST = "nuxeo.db.host";

    protected static final String PARAM_DB_PORT = "nuxeo.db.port";

    protected static final String PARAM_DB_NAME = "nuxeo.db.name";

    protected static final String PARAM_DB_USER = "nuxeo.db.user";

    protected static final String PARAM_DB_PWD = "nuxeo.db.password";

    @Override
    public boolean accepts(ConfigurationHolder configHolder) {
        return !DB_EXCLUDE_CHECK_LIST.contains(configHolder.getProperty(ConfigurationGenerator.PARAM_TEMPLATE_DBTYPE));

    }

    @Override
    public void check(ConfigurationHolder configHolder) throws ConfigurationException {
        try {
            checkDatabaseConnection(configHolder);
        } catch (DatabaseDriverException e) {
            log.debug(e, e);
            log.error(e.getMessage());
            throw new ConfigurationException("Could not find database driver: " + e.getMessage());
        } catch (SQLException e) {
            log.debug(e, e);
            log.error(e.getMessage());
            throw new ConfigurationException("Failed to connect on database: " + e.getMessage());
        }
    }

    /**
     * Check driver availability and database connection
     */
    public void checkDatabaseConnection(ConfigurationHolder configHolder)
            throws ConfigurationException, DatabaseDriverException, SQLException {
        String databaseTemplate = configHolder.getIncludedDBTemplateName();
        String dbName = configHolder.getProperty(PARAM_DB_NAME);
        String dbUser = configHolder.getProperty(PARAM_DB_USER);
        String dbPassword = configHolder.getProperty(PARAM_DB_PWD);
        String dbHost = configHolder.getProperty(PARAM_DB_HOST);
        String dbPort = configHolder.getProperty(PARAM_DB_PORT);

        Path databaseTemplateDir = configHolder.getTemplatesPath().resolve(databaseTemplate);
        String classname = configHolder.getProperty(PARAM_DB_DRIVER);
        String connectionUrl = configHolder.getProperty(PARAM_DB_JDBC_URL);
        // Load driver class from template or default lib directory
        Driver driver = lookupDriver(configHolder, databaseTemplateDir, classname);
        // Test db connection
        DriverManager.registerDriver(driver);
        Properties ttProps = new Properties();
        ttProps.put(PARAM_DB_HOST, dbHost);
        ttProps.put(PARAM_DB_PORT, dbPort);
        ttProps.put(PARAM_DB_NAME, dbName);
        ttProps.put(PARAM_DB_USER, dbUser);
        ttProps.put(PARAM_DB_PWD, dbPassword);
        TextTemplate tt = new TextTemplate(ttProps);
        String url = tt.processText(connectionUrl);
        Properties conProps = new Properties();
        conProps.put("user", dbUser);
        conProps.put("password", dbPassword);
        log.debug("Testing URL: {} with: {}", url, conProps);
        Connection con = driver.connect(url, conProps);
        con.close();
    }

    /**
     * Build an {@link URLClassLoader} for the given databaseTemplate looking in the templates directory and in the
     * server lib directory, then looks for a driver
     *
     * @param classname Driver class name, defined by {@link #PARAM_DB_DRIVER}
     * @return Driver driver if found, else an Exception must have been raised.
     * @throws DatabaseDriverException If there was an error when trying to instantiate the driver.
     * @since 5.6
     */
    protected Driver lookupDriver(ConfigurationHolder configHolder, Path databaseTemplateDir, String classname)
            throws DatabaseDriverException {
        File[] files = ArrayUtils.addAll( //
                databaseTemplateDir.resolve("lib").toFile().listFiles(), //
                configHolder.getHomePath().resolve("lib").toFile().listFiles());
        List<URL> urlsList = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith("jar")) {
                    try {
                        urlsList.add(new URL("jar:file:" + file.getPath() + "!/"));
                        log.debug("Added file: {}", file.getPath());
                    } catch (MalformedURLException e) {
                        log.error(e);
                    }
                }
            }
        }
        URLClassLoader ucl = new URLClassLoader(urlsList.toArray(new URL[0]));
        try {
            return (Driver) Class.forName(classname, true, ucl).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new DatabaseDriverException(e);
        }
    }

}
