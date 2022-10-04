package cipm.consistency.vsum;

import java.nio.file.Path;

import cipm.consistency.base.shared.pcm.LocalFilesystemPCM;

public class File extends FileLayout {

	public File(Path rootDir) {
		super(rootDir);
	}

	public LocalFilesystemPCM getPCM() {
		var filePCM = new LocalFilesystemPCM();
		filePCM.setRepositoryFile(this.getPcmRepositoryPath().toFile());
		filePCM.setAllocationModelFile(this.getPcmAllocationPath().toFile());
		filePCM.setSystemFile(this.getPcmSystemPath().toFile());
		filePCM.setResourceEnvironmentFile(this.getPcmResourceEnvironmentPath().toFile());
		filePCM.setUsageModelFile(this.getPcmUsageModelPath().toFile());
		return filePCM;
	}
}
