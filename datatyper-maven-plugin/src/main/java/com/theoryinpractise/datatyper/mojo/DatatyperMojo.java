package com.theoryinpractise.datatyper.mojo;

import com.theoryinpractise.datatyper.DataTypeCompiler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/** Goal which transpiles .typer files into java source */
@Mojo(
  name = "datatyper",
  defaultPhase = LifecyclePhase.GENERATE_SOURCES,
  requiresDependencyResolution = ResolutionScope.COMPILE
)
public class DatatyperMojo extends AbstractMojo {

  @Parameter(required = true, readonly = true, property = "project")
  protected MavenProject project;

  @Parameter(
    required = true,
    property = "typerDirectory",
    defaultValue = "${basedir}/src/main/datatyper"
  )
  private File typerDirectory;

  @Parameter(
    required = true,
    property = "outputDirectory",
    defaultValue = "${project.build.directory}/generated-sources/datatyper"
  )
  private File outputDirectory;

  public void execute() throws MojoExecutionException {
    File f = outputDirectory;

    if (!f.exists()) {
      f.mkdirs();
    }

    project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

    try {
      discoverTypeFiles(typerDirectory)
          .forEach(typeFile -> DataTypeCompiler.compileFile(typeFile, outputDirectory));
    } catch (IOException e) {
      throw new MojoExecutionException(e.getMessage());
    }
  }

  protected Set<File> discoverTypeFiles(File topPath) throws IOException {
    return Files.find(
            topPath.toPath(),
            10,
            (path, attr) -> path.toFile().getName().endsWith(".typer") && attr.isRegularFile())
        .map(Path::toFile)
        .collect(Collectors.toSet());
  }
}
