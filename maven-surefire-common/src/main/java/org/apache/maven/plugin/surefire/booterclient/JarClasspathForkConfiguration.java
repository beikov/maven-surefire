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

import org.apache.maven.plugin.surefire.booterclient.lazytestprovider.OutputStreamFlushableCommandline;
import org.apache.maven.surefire.booter.Classpath;
import org.apache.maven.surefire.booter.SurefireBooterForkException;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.apache.maven.plugin.surefire.util.Relocator.relocate;
import static org.apache.maven.shared.utils.StringUtils.join;

/**
 * @author <a href="mailto:tibordigana@apache.org">Tibor Digana (tibor17)</a>
 * @since 2.21.0.Jigsaw
 */
public final class JarClasspathForkConfiguration
        extends JarForkConfiguration
{
    @SuppressWarnings( "checkstyle:parameternumber" )
    public JarClasspathForkConfiguration( Classpath bootClasspathConfiguration, File tempDirectory, String debugLine,
                                          File workingDirectory, Properties modelProperties, String argLine,
                                          Map<String, String> environmentVariables, boolean debug, int forkCount,
                                          boolean reuseForks, Platform pluginPlatform )
    {
        super( bootClasspathConfiguration, tempDirectory, debugLine, workingDirectory, modelProperties, argLine,
                environmentVariables, debug, forkCount, reuseForks, pluginPlatform );
    }

    @Override
    protected void resolveClasspath( OutputStreamFlushableCommandline cli, List<String> classPath,
                                     String booterThatHasMainMethod, boolean shadefire )
            throws SurefireBooterForkException
    {
        cli.addEnvironment( "CLASSPATH", join( classPath.iterator(), File.pathSeparator ) );
        cli.createArg().setValue( shadefire ? relocate( booterThatHasMainMethod ) : booterThatHasMainMethod );
    }
}
