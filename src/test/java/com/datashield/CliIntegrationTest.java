package com.datashield;

import com.datashield.cli.DataShieldCli;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class CliIntegrationTest {

    @Test
    void shouldDisplayRootHelpScreen() {
        StringWriter sw = new StringWriter();
        CommandLine cmd = new CommandLine(new DataShieldCli());
        cmd.setOut(new PrintWriter(sw));
        int exitCode = cmd.execute("--help");

        assertThat(exitCode).isEqualTo(0);
        String output = sw.toString();
        assertThat(output).contains("DataShield");
        assertThat(output).contains("backup");
        assertThat(output).contains("restore");
        assertThat(output).contains("test-connection");
        assertThat(output).contains("list");
        assertThat(output).contains("verify");
        assertThat(output).contains("schedule");
    }

    @Test
    void shouldDisplayBackupSubcommandHelp() {
        StringWriter sw = new StringWriter();
        CommandLine cmd = new CommandLine(new DataShieldCli());
        cmd.setOut(new PrintWriter(sw));
        int exitCode = cmd.execute("backup", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = sw.toString();
        assertThat(output).contains("--db-type");
        assertThat(output).contains("--compression");
        assertThat(output).contains("--encrypt");
        assertThat(output).contains("--slack-webhook");
        assertThat(output).contains("--retention-days");
        assertThat(output).contains("--dry-run");
    }

    @Test
    void shouldDisplayRestoreSubcommandHelp() {
        StringWriter sw = new StringWriter();
        CommandLine cmd = new CommandLine(new DataShieldCli());
        cmd.setOut(new PrintWriter(sw));
        int exitCode = cmd.execute("restore", "--help");

        assertThat(exitCode).isEqualTo(0);
        String output = sw.toString();
        assertThat(output).contains("--source");
        assertThat(output).contains("--decrypt");
        assertThat(output).contains("--tables");
        assertThat(output).contains("--dry-run");
    }

    @Test
    void shouldDisplayVersionInformation() {
        StringWriter sw = new StringWriter();
        CommandLine cmd = new CommandLine(new DataShieldCli());
        cmd.setOut(new PrintWriter(sw));
        int exitCode = cmd.execute("--version");

        assertThat(exitCode).isEqualTo(0);
        String output = sw.toString();
        assertThat(output).contains("DataShield Enterprise CLI v1.0.0");
    }
}

