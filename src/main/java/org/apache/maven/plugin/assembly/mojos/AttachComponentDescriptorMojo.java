package org.apache.maven.plugin.assembly.mojos;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;

/**
 * @goal attach-component-descriptor
 * @phase package
 */
public class AttachComponentDescriptorMojo
    extends AbstractMojo
{

    /**
     * @parameter default-value="src/main/resources/assembly-component.xml"
     * @required
     */
    private File componentDescriptor;

    /**
     * If set, component descriptor will be attached to the main project
     * artifact using this classifier, rather than becoming the artifact's
     * backing file.
     *
     * @parameter
     */
    private String attachmentClassifier;

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @component
     */
    private MavenProjectHelper projectHelper;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( attachmentClassifier != null )
        {
            projectHelper.attachArtifact( project, componentDescriptor, attachmentClassifier );
        }
        else
        {
            project.getArtifact().setFile( componentDescriptor );
        }
    }

}
