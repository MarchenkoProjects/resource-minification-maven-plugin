package com.github.marchenkoprojects;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.*;

/**
 * @author Oleg Marchenko
 */
@Mojo(name = "resource-minification", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class ResourceMinificationMojo extends AbstractMojo {
    private static final String DEFAULT_HTML_EXTENSION = "html";
    private static final String DEFAULT_CSS_EXTENSION = "css";
    private static final String DEFAULT_JS_EXTENSION = "js";

    @Parameter(defaultValue = "${basedir}/src/main/webapp")
    private File sourceDirectory;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}")
    private File targetDirectory;

    @Parameter(defaultValue = "[name]-[hash:6].min.[ext]")
    private String filenamePattern;

    @Parameter
    private Set<String> excludeResources;

    private final Map<String, String> resourceToMinifiedResourceMap = new HashMap<>();

    @Override
    public void execute() throws MojoExecutionException {
        Queue<File> htmlResources = new ArrayDeque<>();

        Collection<File> resources = FileUtils.listFiles(sourceDirectory, null, true);
        for (File resource: resources) {
            String filename = resource.getName();
            if (excludeResources.contains(filename)) {
                getLog().info("Skip resource: " + filename);
                continue;
            }

            String fileExtension = FilenameUtils.getExtension(filename);
            switch (fileExtension) {
                case DEFAULT_HTML_EXTENSION:
                    htmlResources.add(resource);
                    break;
                case DEFAULT_CSS_EXTENSION:
                    minifyCssResource(resource);
                    break;
                case DEFAULT_JS_EXTENSION:
                    minifyJsResource(resource);
                    break;
            }
        }

        for (File htmlResource: htmlResources) {
            minifyHtmlResource(htmlResource);
        }
    }

    private void minifyCssResource(File cssResource) {
        getLog().info("Minify CSS resource: " + cssResource.getName());
    }

    private void minifyJsResource(File jsResource) {
        getLog().info("Minify JS resource: " + jsResource.getName());
    }

    private void minifyHtmlResource(File htmlResource) {
        getLog().info("Minify and process HTML resource: " + htmlResource.getName());
    }
}
