package com.datashield.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

@Command(
        name = "datashield",
        description = "DataShield - Enterprise Database Backup & Disaster Recovery CLI for Any Database",
        version = {
                "@|bold,cyan DataShield Enterprise CLI v1.0.0|@",
                "JVM: ${java.version} (${java.vendor} ${java.vm.name})",
                "OS: ${os.name} ${os.version} ${os.arch}"
        },
        mixinStandardHelpOptions = true,
        subcommands = {
                BackupCommand.class,
                RestoreCommand.class,
                TestConnectionCommand.class,
                ListBackupsCommand.class,
                VerifyCommand.class,
                ScheduleCommand.class,
                HelpCommand.class
        },
        headerHeading = "%n@|bold,green " +
                "  ____        _        ____  _     _      _     _ \n" +
                " |  _ \\  __ _| |_ __ _/ ___|| |__ (_) ___| | __| |\n" +
                " | | | |/ _` | __/ _` \\___ \\| '_ \\| |/ _ \\ |/ _` |\n" +
                " | |_| | (_| | || (_| |___) | | | | |  __/ | (_| |\n" +
                " |____/ \\__,_|\\__\\__,_|____/|_| |_|_|\\___|_|\\__,_|\n" +
                " Enterprise Database Backup & Disaster Recovery Utility|@%n%n",
        footerHeading = "%n@|bold Documentation & Support:|@%n",
        footer = "  Run 'datashield <subcommand> --help' for specific command documentation.%n" +
                 "  Supports MySQL, PostgreSQL, MongoDB, SQLite, and Generic JDBC.%n"
)
public class DataShieldCli implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new DataShieldCli());
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        cmd.setUsageHelpWidth(120);
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}

