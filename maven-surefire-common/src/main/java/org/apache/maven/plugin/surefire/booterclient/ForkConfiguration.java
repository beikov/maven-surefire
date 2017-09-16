package org.apache.maven.plugin.surefire.booterclient;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugin.surefire.JdkAttributes;
import org.apache.maven.plugin.surefire.booterclient.lazytestprovider.OutputStreamFlushableCommandline;
import org.apache.maven.surefire.booter.Classpath;
import org.apache.maven.surefire.booter.StartupConfiguration;
import org.apache.maven.surefire.booter.SurefireBooterForkException;
import org.apache.maven.surefire.util.internal.ImmutableMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.apache.maven.plugin.surefire.AbstractSurefireMojo.FORK_NUMBER_PLACEHOLDER;
import static org.apache.maven.plugin.surefire.AbstractSurefireMojo.THREAD_NUMBER_PLACEHOLDER;

/**
 * Configuration for forking tests.
 */
public abstract class ForkConfiguration
{
    @Nonnull public abstract File getTempDirectory();
    @Nullable protected abstract String getDebugLine();
    @Nonnull protected abstract File getWorkingDirectory();
    @Nonnull protected abstract Properties getModelProperties();
    @Nullable protected abstract String getArgLine();
    @Nonnull protected abstract Map<String, String> getEnvironmentVariables();
    protected abstract boolean isDebug();
    protected abstract int getForkCount();
    protected abstract boolean isReuseForks();
    @Nonnull protected abstract Platform getPluginPlatform();
    @Nonnull protected abstract JdkAttributes getJdkForTests();
    @Nonnull protected abstract Classpath getBootClasspath();

    /**
     * @param classPath            the classpath arguments
     * @param config               The startup configuration
     * @param threadNumber         the thread number, to be the replacement in the argLine
     * @return CommandLine able to flush entire command going to be sent to forked JVM
     * @throws org.apache.maven.surefire.booter.SurefireBooterForkException
     *          when unable to perform the fork
     */
    @Nonnull
    public abstract OutputStreamFlushableCommandline createCommandLine( List<String> classPath,
                                                                        StartupConfiguration config, int threadNumber )
            throws SurefireBooterForkException;

    @Nonnull
    protected File getWorkingDirectory( int threadNumber )
        throws SurefireBooterForkException
    {
        File cwd = new File( replaceThreadNumberPlaceholder( getWorkingDirectory().getAbsolutePath(), threadNumber ) );

        if ( !cwd.exists() && !cwd.mkdirs() )
        {
            throw new SurefireBooterForkException( "Cannot create workingDirectory " + cwd.getAbsolutePath() );
        }

        if ( !cwd.isDirectory() )
        {
            throw new SurefireBooterForkException(
                "WorkingDirectory " + cwd.getAbsolutePath() + " exists and is not a directory" );
        }
        return cwd;
    }

    protected static String replaceThreadNumberPlaceholder( String argLine, int threadNumber )
    {
        String threadNumberAsString = String.valueOf( threadNumber );
        return argLine.replace( THREAD_NUMBER_PLACEHOLDER, threadNumberAsString )
                .replace( FORK_NUMBER_PLACEHOLDER, threadNumberAsString );
    }

    /**
     * Replaces expressions <pre>@{property-name}</pre> with the corresponding properties
     * from the model. This allows late evaluation of property values when the plugin is executed (as compared
     * to evaluation when the pom is parsed as is done with <pre>${property-name}</pre> expressions).
     *
     * This allows other plugins to modify or set properties with the changes getting picked up by surefire.
     */
    protected String replacePropertyExpressions()
    {
        if ( getArgLine() == null )
        {
            return "";
        }

        String resolvedArgLine = getArgLine().trim();

        if ( resolvedArgLine.isEmpty() )
        {
            return "";
        }

        for ( final String key : getModelProperties().stringPropertyNames() )
        {
            String field = "@{" + key + "}";
            if ( getArgLine().contains( field ) )
            {
                resolvedArgLine = resolvedArgLine.replace( field, getModelProperties().getProperty( key, "" ) );
            }
        }

        return resolvedArgLine;
    }

    protected static String stripNewLines( String argLine )
    {
        return argLine.replace( "\n", " " ).replace( "\r", " " );
    }

    /**
     * Immutable map.
     *
     * @param map    immutable map copies elements from <code>map</code>
     * @param <K>    key type
     * @param <V>    value type
     * @return never returns null
     */
    protected static <K, V> Map<K, V> toImmutable( Map<K, V> map )
    {
        return map == null ? Collections.<K, V>emptyMap() : new ImmutableMap<K, V>( map );
    }
}
