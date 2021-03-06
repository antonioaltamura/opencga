package org.opencb.opencga.app.cli.internal.executors;

import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.analysis.file.FetchAndRegisterTask;
import org.opencb.opencga.analysis.file.FileDeleteTask;
import org.opencb.opencga.analysis.file.FileIndexTask;
import org.opencb.opencga.app.cli.internal.options.FileCommandOptions;
import org.opencb.opencga.core.exceptions.ToolException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class FileCommandExecutor extends InternalCommandExecutor {

    private final FileCommandOptions fileCommandOptions;

    public FileCommandExecutor(FileCommandOptions options) {
        super(options.fileCommonOptions);
        fileCommandOptions = options;
    }

    @Override
    public void execute() throws Exception {
        logger.debug("Executing file command line");

        String subCommandString = getParsedSubCommand(fileCommandOptions.jCommander);
        configure();
        switch (subCommandString) {
            case "delete":
                delete();
                break;
            case "unlink":
                unlink();
                break;
            case "fetch":
                fetch();
                break;
            case "secondary-index":
                secondaryIndex();
                break;
            default:
                logger.error("Subcommand not valid");
                break;

        }
    }

    private void delete() throws ToolException {
        FileCommandOptions.DeleteCommandOptions options = fileCommandOptions.deleteCommandOptions;

        Path outDir = Paths.get(options.outDir);
        Path opencgaHome = Paths.get(configuration.getWorkspace()).getParent();

        // Prepare analysis parameters and config
        FileDeleteTask delete = new FileDeleteTask()
                .setStudy(options.studyId)
                .setFiles(Arrays.asList(org.apache.commons.lang3.StringUtils.split(options.files, ",")))
                .setSkipTrash(options.skipTrash);

        delete.setUp(opencgaHome.toString(), new ObjectMap(), outDir, options.commonOptions.token);
        delete.start();
    }

    private void unlink() throws ToolException {
        FileCommandOptions.UnlinkCommandOptions options = fileCommandOptions.unlinkCommandOptions;

        Path outDir = Paths.get(options.outDir);
        Path opencgaHome = Paths.get(configuration.getWorkspace()).getParent();

        // Prepare analysis parameters and config
        FileDeleteTask delete = new FileDeleteTask()
                .setStudy(options.studyId)
                .setFiles(Arrays.asList(org.apache.commons.lang3.StringUtils.split(options.files, ",")))
                .setUnlink(true);

        delete.setUp(opencgaHome.toString(), new ObjectMap(), outDir, options.commonOptions.token);
        delete.start();
    }

    private void fetch() throws ToolException {
        FileCommandOptions.FetchCommandOptions options = fileCommandOptions.fetchCommandOptions;

        Path outDir = Paths.get(options.outDir);
        Path opencgaHome = Paths.get(configuration.getWorkspace()).getParent();

        // Prepare analysis parameters and config
        FetchAndRegisterTask download = new FetchAndRegisterTask()
                .setStudy(options.studyId)
                .setPath(options.path)
                .setUrl(options.url);

        download.setUp(opencgaHome.toString(), new ObjectMap(), outDir, options.commonOptions.token);
        download.start();
    }

    private void secondaryIndex() throws ToolException {
        FileCommandOptions.SecondaryIndex options = fileCommandOptions.secondaryIndex;

        Path outDir = Paths.get(options.outDir);
        Path opencgaHome = Paths.get(configuration.getWorkspace()).getParent();

        // Prepare analysis parameters and config
        FileIndexTask indexTask = new FileIndexTask();
        indexTask.setUp(opencgaHome.toString(), new ObjectMap(), outDir, options.commonOptions.token);
        indexTask.start();
    }
}
