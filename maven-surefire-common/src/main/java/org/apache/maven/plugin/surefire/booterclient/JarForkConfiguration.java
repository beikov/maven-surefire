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
import org.apache.maven.surefire.booter.ForkedBooter;
import org.apache.maven.surefire.booter.StartupConfiguration;
import org.apache.maven.surefire.booter.SurefireBooterForkException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * @author <a href="mailto:tibordigana@apache.org">Tibor Digana (tibor17)</a>
 * @since 2.21.0.Jigsaw
 */
abstract class JarForkConfiguration
        extends ForkConfiguration
{
    private static final String ALL_JAVA_API = "--add-modules java.se.ee";
    private static final String ADD_MODULES = "--add-modules";

    @Nonnull private final Classpath bootClasspathConfiguration;
    @Nonnull private final File tempDirectory;
    @Nullable private final String debugLine;
    @Nonnull private final File workingDirectory;
    @Nonnull private final Properties modelProperties;
    @Nullable private final String argLine;
    @Nonnull private final Map<String, String> environmentVariables;
    private final boolean debug;
    private final int forkCount;
    private final boolean reuseForks;
    @Nonnull private final Platform pluginPlatform;

    @SuppressWarnings( "checkstyle:parameternumber" )
    public JarForkConfiguration( @Nonnull Classpath bootClasspathConfiguration,
                                 @Nonnull File tempDirectory,
                                 @Nullable String debugLine,
                                 @Nonnull File workingDirectory,
                                 @Nonnull Properties modelProperties,
                                 @Nullable String argLine,
                                 @Nonnull Map<String, String> environmentVariables,
                                 boolean debug,
                                 int forkCount,
                                 boolean reuseForks,
                                 @Nonnull Platform pluginPlatform )
    {
        this.bootClasspathConfiguration = bootClasspathConfiguration;
        this.tempDirectory = tempDirectory;
        this.debugLine = debugLine;
        this.workingDirectory = workingDirectory;
        this.modelProperties = modelProperties;
        this.argLine = argLine;
        this.environmentVariables = toImmutable( environmentVariables );
        this.debug = debug;
        this.forkCount = forkCount;
        this.reuseForks = reuseForks;
        this.pluginPlatform = pluginPlatform;
    }


    protected abstract void resolveClasspath( OutputStreamFlushableCommandline cli, List<String> classPath,
                                              String booterThatHasMainMethod, boolean shadefire )
            throws SurefireBooterForkException;


    /**
     * @param classPath    the classpath arguments
     * @param config       The startup configuration
     * @param threadNumber the thread number, to be the replacement in the argLine
     * @return CommandLine able to flush entire command going to be sent to forked JVM
     * @throws org.apache.maven.surefire.booter.SurefireBooterForkException when unable to perform the fork
     */
    @Override
    @Nonnull
    public OutputStreamFlushableCommandline createCommandLine( List<String> classPath, StartupConfiguration config,
                                                               int threadNumber ) throws SurefireBooterForkException
    {
        OutputStreamFlushableCommandline cli = new OutputStreamFlushableCommandline();

        cli.setExecutable( getJdkForTests().getJvmExecutable() );

        String jvmArgLine =
                replaceThreadNumberPlaceholder( stripNewLines( replacePropertyExpressions() ), threadNumber );

        if ( getJdkForTests().isJava9AtLeast() && !jvmArgLine.contains( ADD_MODULES ) )
        {
            jvmArgLine = jvmArgLine.isEmpty() ? ALL_JAVA_API : ALL_JAVA_API + " " + jvmArgLine;
        }

        if ( !jvmArgLine.isEmpty() )
        {
            cli.createArg().setLine( jvmArgLine );
        }

        for ( Map.Entry<String, String> entry : getEnvironmentVariables().entrySet() )
        {
            String value = entry.getValue();
            cli.addEnvironment( entry.getKey(), value == null ? "" : value );
        }

        if ( getDebugLine() != null && !getDebugLine().isEmpty() )
        {
            cli.createArg().setLine( getDebugLine() );
        }

        boolean shadefire = config.isShadefire();

        String providerThatHasMainMethod =
                config.isProviderMainClass() ? config.getActualClassName() : ForkedBooter.class.getName();

        resolveClasspath( cli, classPath, providerThatHasMainMethod, shadefire );

        cli.setWorkingDirectory( getWorkingDirectory( threadNumber ).getAbsolutePath() );

        return cli;
    }

    /**
     * Create a jar with just a manifest containing a Main-Class entry for BooterConfiguration and a Class-Path entry
     * for all classpath elements.
     *
     * @param classPath      List&lt;String&gt; of all classpath elements.
     * @param startClassName The classname to start (main-class)
     * @return file of the jar
     * @throws java.io.IOException When a file operation fails.
     */
    @Nonnull
    protected File createJar( List<String> classPath, String startClassName )
            throws IOException
    {
        File file = File.createTempFile( "surefirebooter", ".jar", getTempDirectory() );
        if ( !isDebug() )
        {
            file.deleteOnExit();
        }
        FileOutputStream fos = new FileOutputStream( file );
        JarOutputStream jos = new JarOutputStream( fos );
        try
        {
            jos.setLevel( JarOutputStream.STORED );
            JarEntry je = new JarEntry( "META-INF/MANIFEST.MF" );
            jos.putNextEntry( je );

            Manifest man = new Manifest();

            // we can't use StringUtils.join here since we need to add a '/' to
            // the end of directory entries - otherwise the jvm will ignore them.
            StringBuilder cp = new StringBuilder();
            for ( Iterator<String> it = classPath.iterator(); it.hasNext(); )
            {
                File file1 = new File( it.next() );
                String uri = file1.toURI().toASCIIString();
                cp.append( uri );
                if ( file1.isDirectory() && !uri.endsWith( "/" ) )
                {
                    cp.append( '/' );
                }

                if ( it.hasNext() )
                {
                    cp.append( ' ' );
                }
            }

            man.getMainAttributes().putValue( "Manifest-Version", "1.0" );
            man.getMainAttributes().putValue( "Class-Path", cp.toString().trim() );
            man.getMainAttributes().putValue( "Main-Class", startClassName );

            man.write( jos );

            jos.closeEntry();
            jos.flush();

            return file;
        }
        finally
        {
            jos.close();
        }
    }

    @Override
    @Nonnull
    public File getTempDirectory()
    {
        return tempDirectory;
    }

    @Override
    @Nullable
    protected String getDebugLine()
    {
        return debugLine;
    }

    @Override
    @Nonnull
    protected File getWorkingDirectory()
    {
        return workingDirectory;
    }

    @Override
    @Nonnull
    protected Properties getModelProperties()
    {
        return modelProperties;
    }

    @Override
    @Nullable
    protected String getArgLine()
    {
        return argLine;
    }

    @Override
    @Nonnull
    protected Map<String, String> getEnvironmentVariables()
    {
        return environmentVariables;
    }

    @Override
    protected boolean isDebug()
    {
        return debug;
    }

    @Override
    protected int getForkCount()
    {
        return forkCount;
    }

    @Override
    protected boolean isReuseForks()
    {
        return reuseForks;
    }

    @Override
    @Nonnull
    protected Platform getPluginPlatform()
    {
        return pluginPlatform;
    }

    @Override
    @Nonnull
    protected JdkAttributes getJdkForTests()
    {
        return getPluginPlatform().getJdkExecAttributesForTests();
    }

    @Override
    @Nonnull
    protected Classpath getBootClasspath()
    {
        return bootClasspathConfiguration;
    }
}
