"use strict";

class JasmineTapReporter {
  jasmineStarted() {
    this.results = [];
    process.stdout.write("TAP version 13\n");
  }

  specDone(result) {
    const passed = result.status === "passed";
    const number = this.results.length + 1;
    this.results.push({ name: result.description, passed });
    process.stdout.write(
      `${passed ? "ok" : "not ok"} ${number} - ${result.description}\n`,
    );
  }

  jasmineDone() {
    const passed = this.results.filter((result) => result.passed).length;
    const failed = this.results.length - passed;
    process.stdout.write(`1..${this.results.length}\n`);
    process.stdout.write(`# tests ${this.results.length}\n`);
    process.stdout.write(`# pass ${passed}\n`);
    process.stdout.write(`# fail ${failed}\n`);
    if (failed > 0) process.exitCode = 1;
  }
}

module.exports = JasmineTapReporter;