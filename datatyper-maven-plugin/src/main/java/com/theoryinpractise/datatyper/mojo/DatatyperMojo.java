package com.theoryinpractise.datatyper.mojo;

import com.theoryinpractise.datatyper.GadtCompiler;
import java.io.File;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import rx.Observable;
import rx.Subscriber;

/**
 * Goal which transpiles .gadt files into java source
 */
@Mojo(name = "datatyper", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)

public class DatatyperMojo
    extends AbstractMojo {

  @Parameter(required = true, readonly = true, property = "project")
  protected MavenProject project;

  @Parameter(required = true, property = "gadtDirectory", defaultValue = "${basedir}/src/main/datatyper")
  private File gadtDir;

  @Parameter(required = true, property = "outputDirectory", defaultValue = "${project.build.directory}/generated-sources/datatyper")
  private File outputDirectory;

  public void execute() throws MojoExecutionException {
    File f = outputDirectory;

    if (!f.exists()) {
      f.mkdirs();
    }

    project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

    observeGadtFiles(gadtDir)
        .toBlocking()
        .forEach(gadtFile -> GadtCompiler.compileFile(gadtFile, outputDirectory));

  }


  protected Observable<File> observeGadtFiles(File topPath) {
    return observeSubDirectories(topPath)
        .flatMap(path -> Observable.from(path.listFiles(file -> file.getName().endsWith(".typer"))));
  }


  protected Observable<File> observeSubDirectories(File topPath) {
    return Observable.create(new Observable.OnSubscribe<File>() {
      @Override
      public void call(Subscriber<? super File> subscriber) {
        subscriber.onStart();
        processDirectoriesFromDirectory(topPath, subscriber);
        subscriber.onCompleted();
      }

      private void processDirectoriesFromDirectory(File input, Subscriber<? super File> subscriber) {

        if (input.isDirectory()) {
          subscriber.onNext(input);
          File[] files = input.listFiles();
          for (File file : files) {
            if (file.isDirectory()) {
              processDirectoriesFromDirectory(file, subscriber);
            }
          }
        } else {
          subscriber.onError(new IllegalStateException("Path is not a directory: " + input.toString()));
        }
      }

    });
  }


}
