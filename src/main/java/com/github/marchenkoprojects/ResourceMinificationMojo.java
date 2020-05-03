package com.github.marchenkoprojects;

import com.google.common.hash.Hashing;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.googlecode.htmlcompressor.compressor.HtmlCompressor;
import com.googlecode.htmlcompressor.compressor.YuiCssCompressor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

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

    private final Map<String, String> resourceToMinifiedResourceRegistry = new HashMap<>();

    @Override
    public void execute() throws MojoExecutionException {
        Queue<File> htmlResources = new ArrayDeque<>();

        Collection<File> resources = FileUtils.listFiles(sourceDirectory, null, true);
        for (File resource: resources) {
            String resourceName = resource.getName();
            if (excludeResources.contains(resourceName)) {
                getLog().info("Skip resource: " + resourceName);
                continue;
            }

            String resourceExtension = FilenameUtils.getExtension(resourceName);
            switch (resourceExtension) {
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

    private void minifyCssResource(File resource) {
        String resourceName = resource.getName();
        getLog().info("Minify CSS resource: " + resourceName);

        String content = readResource(resource);

        YuiCssCompressor cssCompressor = new YuiCssCompressor();
        String compressedContent = cssCompressor.compress(content);

        String resourceTargetDirectory = buildResourceTargetDirectory(resource);
        String resourceFilename = generateResourceFilename(resource, compressedContent);
        File compressedResource = new File(resourceTargetDirectory + resourceFilename);
        writeResource(compressedResource, compressedContent);

        resourceToMinifiedResourceRegistry.put(resourceName, compressedResource.getName());
    }

    private void minifyJsResource(File resource) {
        String resourceName = resource.getName();
        getLog().info("Minify JS resource: " + resourceName);

        CompilerOptions options = new CompilerOptions();
        CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel(options);
        options.setEmitUseStrict(false);
        options.setCollapseObjectLiterals(true);

        Compiler compiler = new Compiler();
        compiler.compile(emptyList(), singletonList(SourceFile.fromFile(resource.getPath())), options);
        String compressedContent = compiler.toSource();

        String resourceTargetDirectory = buildResourceTargetDirectory(resource);
        String resourceFilename = generateResourceFilename(resource, compressedContent);
        File compressedResource = new File(resourceTargetDirectory + resourceFilename);
        writeResource(compressedResource, compressedContent);

        resourceToMinifiedResourceRegistry.put(resourceName, compressedResource.getName());
    }

    private void minifyHtmlResource(File resource) {
        String resourceName = resource.getName();
        getLog().info("Minify and process HTML resource: " + resourceName);

        HtmlCompressor htmlCompressor = new HtmlCompressor();
        htmlCompressor.setRemoveIntertagSpaces(true);

        String content = readResource(resource);
        String compressedContent = htmlCompressor.compress(content);
        String processedContent = processHtmlResource(compressedContent);

        String resourceTargetDirectory = buildResourceTargetDirectory(resource);
        File compressedResource = new File(resourceTargetDirectory + resourceName);
        writeResource(compressedResource, processedContent);
    }

    private String processHtmlResource(String htmlContent) {
        String fileContent = htmlContent;
        for (String filename: resourceToMinifiedResourceRegistry.keySet()) {
            String minifiedFilename = resourceToMinifiedResourceRegistry.get(filename);
            fileContent = fileContent.replace(filename, minifiedFilename);
        }
        return fileContent;
    }

    private String buildResourceTargetDirectory(File resource) {
        return targetDirectory +
                resource.getParent()
                        .replace(sourceDirectory.getPath(), "")
                        .replace(resource.getName(), "") +
                File.separator;
    }

    private String generateResourceFilename(File file, String fileContent) {
        String filename = file.getName();
        String baseName = FilenameUtils.getBaseName(filename);
        String extension = FilenameUtils.getExtension(filename);

        String generatedFilename = filenamePattern
                .replace("[name]", baseName)
                .replace("[ext]", extension);

        if (filenamePattern.contains("[hash")) {
            String fileHash = generateHash(fileContent);
            if (filenamePattern.contains("[hash]")) {
                generatedFilename = generatedFilename.replace("[hash]", fileHash);
            }
            else if (filenamePattern.contains("[hash:")) {
                int hashSize = Integer.parseInt(generatedFilename.substring(generatedFilename.indexOf("[hash:") + 6,
                                                                            generatedFilename.lastIndexOf(']')));
                String hashPart = fileHash.substring(0, hashSize);
                generatedFilename = generatedFilename.replace("[hash:" + hashSize + "]", hashPart);
            }
        }
        return generatedFilename;
    }

    private static String readResource(File file) {
        try {
            return FileUtils.readFileToString(file, "UTF-8");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeResource(File file, String content) {
        try {
            FileUtils.write(file, content, "UTF-8");
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String generateHash(String content) {
        return Hashing.md5().hashBytes(content.getBytes()).toString();
    }
}
