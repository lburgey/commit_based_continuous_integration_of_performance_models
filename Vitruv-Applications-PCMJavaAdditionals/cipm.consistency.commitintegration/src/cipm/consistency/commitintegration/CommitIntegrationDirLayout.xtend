package cipm.consistency.commitintegration

import cipm.consistency.models.ModelDirLayoutImpl
import java.nio.file.Path
import org.eclipse.xtend.lib.annotations.Accessors

@Accessors
class CommitIntegrationDirLayout extends ModelDirLayoutImpl {
	
	static String vsumDirName = "vsum"
	static String imDirName = "im"
	static String pcmDirName = "pcm"
	static String codeDirName = "code"
	
	static String commitsFileName = "commits"
	static String settingsFileName = "settings.settings"
	
	Path vsumDirPath
	Path pcmDirPath
	Path imDirPath
	Path codeDirPath
	
	Path commitsFilePath
	Path settingsFilePath
	
	override initialize(Path rootDirPath) {
		super.initialize(rootDirPath)
		
		vsumDirPath = rootDirPath.resolve(vsumDirName)
		pcmDirPath = rootDirPath.resolve(pcmDirName)
		imDirPath = rootDirPath.resolve(imDirName)
		codeDirPath = rootDirPath.resolve(codeDirName)
		
		commitsFilePath = rootDirPath.resolve(commitsFileName)
		settingsFilePath = rootDirPath.resolve(settingsFileName)
	}
	
}