/*
 * SonarQube Runner - CLI - Distribution
 * Copyright (C) 2011 SonarSource
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.runner.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public class StatsTest {
  Logs logs = new Logs();

  @Test
  public void shouldPrintStats() throws UnsupportedEncodingException {

    PrintStream backupOut = System.out;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(baos));

      new Stats(logs).start().stop();

      String out = baos.toString();
      String[] lines = out.split(System.lineSeparator());

      assertThat(lines).hasSize(2);

      assertThat(lines[0]).contains("Total time: ");
      assertThat(lines[1]).contains("Final Memory: ");
    } finally {
      System.setOut(backupOut);
    }
  }

  @Test
  public void shouldFormatTime() {
    assertThat(Stats.formatTime(1 * 60 * 60 * 1000 + 2 * 60 * 1000 + 3 * 1000 + 400)).isEqualTo("1:02:03.400s");
    assertThat(Stats.formatTime(2 * 60 * 1000 + 3 * 1000 + 400)).isEqualTo("2:03.400s");
    assertThat(Stats.formatTime(3 * 1000 + 400)).isEqualTo("3.400s");
    assertThat(Stats.formatTime(400)).isEqualTo("0.400s");
  }
}
