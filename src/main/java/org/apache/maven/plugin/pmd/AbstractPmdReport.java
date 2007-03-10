package org.apache.maven.plugin.pmd;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.codehaus.doxia.site.renderer.SiteRenderer;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.PathTool;
import org.codehaus.plexus.util.StringUtils;

/**
 * Base class for the PMD reports.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public abstract class AbstractPmdReport
    extends AbstractMavenReport
{
    /**
     * The output directory for the intermediate XML report.
     *
     * @parameter expression="${project.build.directory}"
     * @required
     */
    protected File targetDirectory;

    /**
     * The output directory for the final HTML report.
     *
     * @parameter expression="${project.reporting.outputDirectory}"
     * @required
     */
    protected String outputDirectory;

    /**
     * Site rendering component for generating the HTML report.
     *
     * @component
     */
    private SiteRenderer siteRenderer;

    /**
     * The project to analyse.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * Set the output format type, in addition to the HTML report.  Must be one of: "none",
     * "csv", "xml", "txt" or the full class name of the PMD renderer to use.
     * See the net.sourceforge.pmd.renderers package javadoc for available renderers.
     * XML is required if the pmd:check goal is being used.
     *
     * @parameter expression="${format}" default-value="xml"
     */
    protected String format = "xml";

    /**
     * Link the violation line numbers to the source xref. Links will be created
     * automatically if the jxr plugin is being used.
     *
     * @parameter expression="${linkXRef}" default-value="true"
     */
    private boolean linkXRef;

    /**
     * Location of the Xrefs to link to.
     *
     * @parameter default-value="${project.reporting.outputDirectory}/xref"
     */
    private File xrefLocation;

    /**
     * A list of files to exclude from checking. Can contain ant-style wildcards and double wildcards.
     *
     * @parameter
     * @since 2.2
     */
    private String[] excludes;
    
    /**
     * A list of files to include from checking. Can contain ant-style wildcards and double wildcards.  
     * Defaults to **\/*.java
     *
     * @since 2.2
     * @parameter
     */
    private String[] includes;

    
    
    
    /**
     * The source directories containing the sources to be compiled.
     *
     * @parameter expression="${project.compileSourceRoots}"
     * @required
     * @readonly
     */
    private List compileSourceRoots;
    
    /**
     * The source directories containing the test-sources to be compiled.
     *
     * @parameter expression="${project.testCompileSourceRoots}"
     * @required
     * @readonly
     */
    private List testSourceRoots;
    
    /**
     * The project source directories that should be excluded.
     *
     * @since 2.2
     * @parameter
     */
    private List excludeRoots;
    
    /**
     * Run PMD on the tests
     *
     * @parameter default-value="false"
     * @since 2.2
     */
    protected boolean includeTests;
    

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getProject()
     */
    protected MavenProject getProject()
    {
        return project;
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getSiteRenderer()
     */
    protected SiteRenderer getSiteRenderer()
    {
        return siteRenderer;
    }

    protected String constructXRefLocation()
    {
        String location = null;
        if ( linkXRef )
        {
            String relativePath = PathTool.getRelativePath( outputDirectory, xrefLocation.getAbsolutePath() );
            if ( StringUtils.isEmpty( relativePath ) )
            {
                relativePath = ".";
            }
            relativePath = relativePath + "/" + xrefLocation.getName();
            if ( xrefLocation.exists() )
            {
                // XRef was already generated by manual execution of a lifecycle binding
                location = relativePath;
            }
            else
            {
                // Not yet generated - check if the report is on its way
                for ( Iterator reports = project.getReportPlugins().iterator(); reports.hasNext(); )
                {
                    ReportPlugin plugin = (ReportPlugin) reports.next();

                    String artifactId = plugin.getArtifactId();
                    if ( "maven-jxr-plugin".equals( artifactId ) || "jxr-maven-plugin".equals( artifactId ) )
                    {
                        location = relativePath;
                    }
                }
            }

            if ( location == null )
            {
                getLog().warn( "Unable to locate Source XRef to link to - DISABLED" );
            }
        }
        return location;
    }

    /**
     * Convenience method to get the list of files where the PMD tool will be executed
     *
     * @param includes contains the concatenated list of files to be included
     * @return a List of the files where the PMD tool will be executed
     * @throws java.io.IOException
     */
    protected List getFilesToProcess( )
        throws IOException
    {
        if ( excludeRoots == null )
        {
            excludeRoots = Collections.EMPTY_LIST;
        }
        List excludeRootFiles = new ArrayList(excludeRoots.size());
        
        for (Iterator it = excludeRoots.iterator(); it.hasNext();) 
        {
            String root = (String)it.next();
            File file = new File(root);
            if ( file.exists()
                && file.isDirectory())
            {
                excludeRootFiles.add(file);
            }
        }
        
        String excluding = getIncludeExcludeString( excludes );
        String including = getIncludeExcludeString( includes );
        List files = new ArrayList();
        
        if ( "".equals(including) )
        {
            including = "**/*.java";
        }

        StringBuffer excludesStr = new StringBuffer();
        if ( StringUtils.isNotEmpty( excluding ) )
        {
            excludesStr.append( excluding );
        }
        String[] defaultExcludes = FileUtils.getDefaultExcludes();
        for ( int i = 0; i < defaultExcludes.length; i++ )
        {
            if ( excludesStr.length() > 0 )
            {
                excludesStr.append( "," );
            }
            excludesStr.append( defaultExcludes[i] );
        }
        getLog().debug( "Excluded files: '" + excludesStr + "'" );

        for ( Iterator it = compileSourceRoots.iterator(); it.hasNext();)
        {
            String root = (String)it.next();
            File sourceDirectory = new File(root);
            if ( sourceDirectory.exists()
                && sourceDirectory.isDirectory()
                && !excludeRootFiles.contains(sourceDirectory))
            {
                files.addAll( FileUtils.getFiles( sourceDirectory, including, excludesStr.toString() ) );
            }
        }
        
        if ( includeTests )
        {
            for ( Iterator it = testSourceRoots.iterator(); it.hasNext();)
            {
                String root = (String)it.next();
                File sourceDirectory = new File(root);
                
                if ( sourceDirectory.exists()
                    && sourceDirectory.isDirectory()
                    && !excludeRootFiles.contains(sourceDirectory))
                {
                    files.addAll( FileUtils.getFiles( sourceDirectory, including, excludesStr.toString() ) );
                }
            }
        }
        return files;
    }

    /**
     * Convenience method that concatenates the files to be excluded into the appropriate format
     *
     * @param exclude the array of Strings that contains the files to be excluded
     * @return a String that contains the concatenates file names
     */
    private String getIncludeExcludeString( String[] arr )
    {
        StringBuffer str = new StringBuffer();

        if ( arr != null )
        {
            for ( int index = 0; index < arr.length; index++ )
            {
                if ( str.length() > 0 )
                {
                    str.append( ',' );
                }
                str.append( arr[index] );
            }
        }

        return str.toString();
    }


    protected boolean isHtml()
    {
        return "html".equals( format );
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#canGenerateReport()
     */
    public boolean canGenerateReport()
    {
        ArtifactHandler artifactHandler = project.getArtifact().getArtifactHandler();
        return "java".equals( artifactHandler.getLanguage() ) &&
            new File( project.getBuild().getSourceDirectory() ).exists();
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getOutputDirectory()
     */
    protected String getOutputDirectory()
    {
        return outputDirectory;
    }
}
