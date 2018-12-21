package synercys.rts.scheduler.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.*;
import synercys.rts.framework.TaskSet;
import synercys.rts.scheduler.TaskSetContainer;
import synercys.rts.scheduler.TaskSetGenerator;
import synercys.rts.util.JsonLogExporter;
import synercys.rts.util.JsonLogLoader;

import java.util.ArrayList;
import java.util.concurrent.Callable;

/**
 * Created by cy on 1/6/2018.
 */

@CommandLine.Command(name = "rttaskgen", versionProvider = synercys.rts.RtsConfig.class,  header = "@|blue | Real-time Task Generator | |@")
public class RtTaskGen implements Callable {
    private static final Logger loggerConsole = LogManager.getLogger("console");

    @Option(names = {"-V", "--version"}, versionHelp = true, description = "Display version info.")
    boolean versionInfoRequested;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message.")
    boolean usageHelpRequested;

    @Option(names = {"-n", "--size"}, required = false, description = "The number of tasks in a task set. It is ignored when a configuration file is specified (-i).")
    int taskSize = 5;

    @Option(names = {"-i", "--in"}, required = false, description = "A file that contains task configurations.")
    String taskInputFile = "";

    @Option(names = {"-o", "--out"}, required = false, description = "A file for storing generated task sets.")
    String outputFilePrefix = "";

    @Option(names = {"-r", "--read"}, required = false, description = "A taskset file to be read and printed. This option ignores other options.")
    String tasksetFileToBeReadAndPrinted = "";

    @Option(names = {"-c", "--config"}, required = false, description = "Create a configuration file with default configuration.")
    String generateDefaultConfigFile = "";

    public static void main(String... args) {
        /* A few test command and parameters. Uncomment one to test it. */
        // args = String[]{"-h"};
        // args = String[]{"-n", "5", "-i", "sampleLogs/task_config.txt", "-o", "sampleLogs/5tasks.tasksets"};

        CommandLine commandLine = new CommandLine(new RtTaskGen());
        try {
            commandLine.parse(args);
        } catch (MissingParameterException ex) {
            System.err.println(ex.getMessage());
            System.err.println("Use -h to see required options.");
            return;
        }
        if (commandLine.isUsageHelpRequested()) {
            commandLine.usage(System.out);
            return;
        } else if (commandLine.isVersionHelpRequested()) {
            commandLine.printVersionHelp(System.out);
            return;
        }
        CommandLine.call(new RtTaskGen(), System.err, args);
    }

    @Override
    public Object call() throws Exception {

        /* If reading and printing a taskset file is requested, then we'll ignore all other requests. */
        if (!tasksetFileToBeReadAndPrinted.equalsIgnoreCase("")) {
            JsonLogLoader jsonLogLoader = new JsonLogLoader(tasksetFileToBeReadAndPrinted);
            try {
                TaskSetContainer taskSetContainer = (TaskSetContainer) jsonLogLoader.getResult();
                int taskSetCount = 1;
                for (TaskSet taskSet : taskSetContainer.getTaskSets()) {
                    loggerConsole.info("#TaskSet {}:", String.valueOf(taskSetCount));
                    loggerConsole.info(taskSet.toString());
                    loggerConsole.info(" ");
                    taskSetCount++;
                }
            } catch (Exception e) {
                loggerConsole.error(e);
                return null;
            }
            return null;
        }

        /* If generating default configuration file is requested, then we'll ignore other options. */
        if (!generateDefaultConfigFile.equalsIgnoreCase("")) {
            JsonLogExporter taskGenConfigExporter = new JsonLogExporter(generateDefaultConfigFile);
            taskGenConfigExporter.exportRtTaskGenDefaultSettings();
            loggerConsole.info("Default configuration is exported.");
            return null;
        }

        TaskSetContainer taskSetContainer = new TaskSetContainer();
        if (taskInputFile.equalsIgnoreCase("")) {
            // Generate a task set using default values.
            TaskSetGenerator taskSetGenerator = new TaskSetGenerator();
            taskSetContainer.addTaskSet(taskSetGenerator.generate(taskSize, 1).getTaskSets().get(0));
        } else {
            // Generate task sets using settings from the input file.
            JsonLogLoader jsonLogLoader = new JsonLogLoader(taskInputFile);
            ArrayList<TaskSetGenerator> taskSetGenerators = (ArrayList<TaskSetGenerator>) jsonLogLoader.getResult();

            for (TaskSetGenerator taskSetGenerator : taskSetGenerators) {
                TaskSetContainer thisTaskSetContainer = taskSetGenerator.generate();
                taskSetContainer.addTaskSets(thisTaskSetContainer.getTaskSets());
            }
        }

        if (!outputFilePrefix.equalsIgnoreCase("")) {
            JsonLogExporter logExporter = new JsonLogExporter(outputFilePrefix);

            if (taskSetContainer.size() == 1) {
                logExporter.exportSingleTaskSet(taskSetContainer.getTaskSets().get(0));
            } else {
                logExporter.exportTaskSets(taskSetContainer);
            }
        }

        int taskSetId = 0;
        for (TaskSet taskSet : taskSetContainer.getTaskSets()) {
            taskSet.setId(taskSetId);
            loggerConsole.info(taskSet.toString());
            taskSetId++;
        }

        loggerConsole.info("{} {} generated.", String.valueOf(taskSetContainer.size()), taskSetContainer.size()==1?"task set is":"task sets are");

        return null;
    }
}