package io.github.gravedig.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "gravedig",
        description = "Dig up the secrets your git history forgot to bury.",
        mixinStandardHelpOptions = true,
        version = "gravedig 0.1.0",
        subcommands = {
                ScanCommand.class,
                HookCommand.class,
                UiCommand.class,
                BenchmarkCommand.class
        }
)
public class GravedigCli implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    static {
        System.setProperty("jgit.fs.filestoreattributes.save", "false");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new GravedigCli()).execute(args);
        System.exit(exitCode);
    }
}
