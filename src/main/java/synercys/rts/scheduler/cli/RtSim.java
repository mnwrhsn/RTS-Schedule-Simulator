package synercys.rts.scheduler.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.*;
import synercys.rts.analysis.dft.ScheduleDFTAnalysisReport;
import synercys.rts.analysis.dft.ScheduleDFTAnalyzer;
import synercys.rts.framework.event.BusyIntervalEventContainer;
import synercys.rts.framework.event.EventContainer;
import synercys.rts.framework.TaskSet;
import synercys.rts.scheduler.*;
import synercys.rts.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * RtSim.java
 * Purpose: A command to run real-time schedule simulation. Currently it supports the EDF and RM schedulers.
 *
 * @author CY Chen (cchen140@illinois.edu)
 * @version 1.1 - 2018, 12/23
 * @version 1.0 - 2018, 2/19
 */
@Command(name = "rtsim", versionProvider = synercys.rts.RtsConfig.class, header = "@|blue | RT Schedule Simulator | |@")
public class RtSim implements Callable {
    protected static final Logger loggerConsole = LogManager.getLogger("console");

    @Option(names = {"-V", "--version"}, versionHelp = true, description = "Display version info.")
    protected boolean versionInfoRequested;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message.")
    protected boolean usageHelpRequested;

    @Option(names = {"-i", "--in"}, required = true, description = "A file that contains taskset parameters.")
    protected String taskInputFile = "";

    @Option(names = {"-o", "--out"}, required = false, description =
            "File names (including their formats) for schedule simulation output. " +
            "The output format is determined by the given file extension: " +
            "\".xlsx\", \".txt\", \".rtschedule\", \".rtdft\" (for DFT analysis).")
    protected List<String> outputFilePathAndFormat = new ArrayList<>();

    @CommandLine.Option(names = {"-p", "--policy"}, required = true, description = "Scheduling policy (\"--option\" for detailed options).")
    protected String schedulingPolicy = "";

    @Option(names = {"-d", "--duration"}, required = true, description = "Simulation duration in 0.1ms (e.g., 10 is 1ms).")
    protected long simDuration = 0;

    @Option(names = {"-b", "--bibs"}, required = false, description = "Output busy intervals as binary string.")
    protected boolean optionGenBisBinaryString = false;

    @Option(names = {"-l", "--ladder"}, required = false, description = "Applicable for xlsx format. Width of a ladder diagram.")
    protected long optionLadderDiagramWidth = 0;

    @Option(names = {"-r", "--rounds"}, required = false, description = "The number of simulation rounds to be carried.")
    protected long optionRounds = 1;

    @Option(names = {"-v", "--evar"}, required = false, description = "Enable execution time variation.")
    protected boolean optionExecutionVariation = false;

    @CommandLine.Option(names = {"--options"}, required = false, description = "Show all option names.")
    protected boolean showOptionNames = false;

    protected TaskSet taskSet = null;
    protected EventContainer eventContainer = null;

    ExcelLogHandler excelLogHandler = null;

    public static void main(String... args) {
        /* A few test command and parameters. Uncomment one to test it. */
        // args = new String[]{"-h"};
        // args = new String[]{"-i", "sampleLogs/5tasks.tasksets", "-o", "sampleLogs/5tasks_out.txt", "-o", "sampleLogs/5tasks_out.xlsx", "-l", "200", "-o", "sampleLogs/5tasks_out.rtschedule", "-d", "10000", "-p", "EDF"};
        // args = new String[]{"-i", "sampleLogs/5tasks.tasksets", "-d", "100", "-p", "EDF"};

        CommandLine commandLine = new CommandLine(new RtSim());
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
        CommandLine.call(new RtSim(), System.err, args);
    }

    @Override
    public Object call() throws Exception {

        if (showOptionNames) {
            loggerConsole.info("All supported options:");
            loggerConsole.info("Scheduling Algorithms = {}", SchedulerUtil.getSchedulerNames());
            return null;
        }

        if (importTaskSet() == false) {
            loggerConsole.error("Failed to import the taskset.");
            return null;
        }

        for (int round=1; round<=optionRounds; round++) {
            if (runScheduleSimulation() == false) {
                loggerConsole.error("Unknown scheduler: \"{}\"", schedulingPolicy);
                return null;
            }

            // Build busy intervals for ScheduLeak
            BusyIntervalEventContainer biEvents = new BusyIntervalEventContainer();
            biEvents.createBusyIntervalsFromEvents(eventContainer);
            //biEvents.removeBusyIntervalsBeforeButExcludeTimeStamp(victimTask.getInitialOffset());


            /* Output generation */
            for (int i = 0; i < outputFilePathAndFormat.size(); i++) {
                String thisOutputFileName = outputFilePathAndFormat.get(i);

                /* Extract the extension name. */
                String outputExtension = "";
                String fileNamePrefix = "";
                int extensionNameIndex = thisOutputFileName.lastIndexOf('.');
                if (extensionNameIndex > 0) {
                    outputExtension = thisOutputFileName.substring(extensionNameIndex + 1);
                    fileNamePrefix = thisOutputFileName.substring(0, extensionNameIndex);
                } else {
                    fileNamePrefix = thisOutputFileName;
                }

                if (outputExtension.equalsIgnoreCase("txt")) {
                    loggerConsole.info("Generate output in txt format.");

                    if (optionRounds > 1) {
                        thisOutputFileName = fileNamePrefix + "_" + round + ".txt";
                    }

                    LogExporter logExporter = new LogExporter();
                    logExporter.openToWriteFile(thisOutputFileName);
                    if (optionGenBisBinaryString == true)
                        logExporter.exportBusyIntervalsBinaryString(biEvents);
                    else
                        logExporter.exportRawScheduleString(eventContainer);
                } else if (outputExtension.equalsIgnoreCase("xlsx")) {
                    loggerConsole.info("Generate output in xlsx format.");

                    //ExcelLogHandler excelLogHandler;
                    if (round == 1) {
                        // Create Excel file from scratch
                        excelLogHandler = new ExcelLogHandler();
                    } else {
                        // Open the existing Excel file and append
                        //excelLogHandler = new ExcelLogHandler(thisOutputFileName);
                        ;
                    }

                    if (optionLadderDiagramWidth > 0) {
                        // Width for the ladder diagram is specified, so output with a ladder diagram.
                        excelLogHandler.genSchedulerIntervalEventsOnLadderDiagram(eventContainer, optionLadderDiagramWidth);
                    } else {
                        // Output normal schedule format in a single row.
                        excelLogHandler.genRowSchedulerIntervalEvents(eventContainer, (optionRounds==1));
                    }

                    if (round == optionRounds)
                        excelLogHandler.saveAndClose(thisOutputFileName);
                } else if (outputExtension.equalsIgnoreCase("rtschedule")) {
                    loggerConsole.info("Generate output in rtschedule (json) format.");

                    if (optionRounds > 1) {
                        thisOutputFileName = fileNamePrefix + "_" + round + ".rtschedule";
                    }

                    JsonLogExporter jsonLogExporter = new JsonLogExporter(thisOutputFileName);
                    jsonLogExporter.exportRawSchedule(eventContainer);
                } else if (outputExtension.equalsIgnoreCase("rtdft")) {
                    loggerConsole.info("Run and generate FFT analysis.");
                    ScheduleDFTAnalyzer dftAnalyzer = new ScheduleDFTAnalyzer();
                    dftAnalyzer.setTaskSet(taskSet);
                    dftAnalyzer.setBinarySchedule(eventContainer);
                    ScheduleDFTAnalysisReport dftReport = dftAnalyzer.computeFreqSpectrum();

                    if (optionRounds > 1) {
                        thisOutputFileName = fileNamePrefix + "_" + round + ".rtdft";
                    }

                    JsonLogExporter jsonLogExporter = new JsonLogExporter(thisOutputFileName);
                    jsonLogExporter.exportDFTAnalysisReport(dftReport);
                } else {
                    loggerConsole.info("Invalid output extension.");
                }
            }

            loggerConsole.info(eventContainer.getAllEvents());
        }

        return null;
    }

    protected boolean importTaskSet() {
        JsonLogLoader jsonLogLoader = new JsonLogLoader(taskInputFile);
        try {
            TaskSetContainer taskSetContainer = (TaskSetContainer) jsonLogLoader.getResult();
            taskSet = taskSetContainer.getTaskSets().get(0);
        } catch (Exception e) {
            //loggerConsole.error(e);
            return false;
        }
        loggerConsole.info(taskSet.toString());
        return true;
    }

    protected boolean runScheduleSimulation() {
        AdvanceableSchedulerInterface scheduler;
        scheduler = SchedulerUtil.getScheduler(schedulingPolicy, taskSet, optionExecutionVariation);

        loggerConsole.info("{} selected.", scheduler.getClass().getName());

        eventContainer = scheduler.runSim(simDuration);

        if (eventContainer != null)
            return true;
        else
            return false;
    }
}
