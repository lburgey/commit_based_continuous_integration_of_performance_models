package cipm.consistency.commitintegration.lang.detection.java;

import cipm.consistency.commitintegration.lang.detection.ComponentState;
import cipm.consistency.commitintegration.lang.detection.strategy.BuildFileBasedComponentDetectionStrategy;
import java.nio.file.Path;

/**
 * A component discovery strategy which considers build and deployment configuration files.
 * 
 * @author Martin Armbruster
 */
public class JavaBuildFileBasedComponentDetectionStrategy extends BuildFileBasedComponentDetectionStrategy {
    private static final String MAVEN_POM_FILE_NAME = "pom.xml";
    private static final String GRADLE_BUILD_FILE_NAME = "build.gradle";
    private static final String DOCKERFILE_FILE_NAME = "Dockerfile";

    @Override
    protected ComponentState checkDirectoryForComponent(Path parent) {
        if (checkBuildFileExistence(parent)) {
            boolean dockerFileExists = checkSiblingExistence(parent, DOCKERFILE_FILE_NAME);
            if (dockerFileExists) {
                return ComponentState.MICROSERVICE_COMPONENT;
            }
            return ComponentState.COMPONENT_CANDIDATE;
        }
        return null;
    }

    protected boolean checkBuildFileExistence(Path parent) {
        return checkSiblingExistence(parent, MAVEN_POM_FILE_NAME)
                || checkSiblingExistence(parent, GRADLE_BUILD_FILE_NAME);
    }
}
