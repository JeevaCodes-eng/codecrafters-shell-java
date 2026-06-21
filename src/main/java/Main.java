if (stdoutFile != null) {
    File out = new File(stdoutFile);

    processBuilder.redirectOutput(
            appendStdout
                    ? ProcessBuilder.Redirect.appendTo(out)
                    : ProcessBuilder.Redirect.to(out));
} else {
    processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
}

if (stderrFile != null) {
    File err = new File(stderrFile);

    processBuilder.redirectError(
            appendStderr
                    ? ProcessBuilder.Redirect.appendTo(err)
                    : ProcessBuilder.Redirect.to(err));
} else {
    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
}