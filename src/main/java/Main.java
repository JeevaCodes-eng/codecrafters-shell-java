import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static class Job {
        int id;
        long pid;
        String command;
        Process process;

        Job(int id, long pid, String command, Process process) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }
    }

    private static final List<Job> jobsList = new ArrayList<>();
    private static int nextJobId = 1;

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            boolean isBackground = false;

            if (input.endsWith("&")) {
                isBackground = true;
                input = input.substring(0, input.length() - 1).trim();

                if (input.isEmpty()) {
                    continue;
                }
            }

            List<String> parsedArgs = parseArguments(input);

            if (parsedArgs.isEmpty()) {
                continue;
            }

            String stdoutFile = null;
            String stderrFile = null;
            boolean appendStdout = false;
            boolean appendStderr = false;

            List<String> commandArgs = new ArrayList<>();

            for (int i = 0; i < parsedArgs.size(); i++) {
                String arg = parsedArgs.get(i);

                if ((arg.equals(">") || arg.equals("1>")) && i + 1 < parsedArgs.size()) {
                    stdoutFile = parsedArgs.get(++i);
                } else if ((arg.equals(">>") || arg.equals("1>>")) && i + 1 < parsedArgs.size()) {
                    stdoutFile = parsedArgs.get(++i);
                    appendStdout = true;
                } else if (arg.equals("2>") && i + 1 < parsedArgs.size()) {
                    stderrFile = parsedArgs.get(++i);
                } else if (arg.equals("2>>") && i + 1 < parsedArgs.size()) {
                    stderrFile = parsedArgs.get(++i);
                    appendStderr = true;
                } else {
                    commandArgs.add(arg);
                }
            }

            if (commandArgs.isEmpty()) {
                continue;
            }

            String command = commandArgs.get(0);

            if (command.equals("exit")) {
                int code = commandArgs.size() > 1
                        ? Integer.parseInt(commandArgs.get(1))
                        : 0;

                System.exit(code);
            }

            if (command.equals("echo")) {
                String output = String.join(" ", commandArgs.subList(1, commandArgs.size()));

                if (stdoutFile != null) {
                    writeToFile(stdoutFile, output + "\n", appendStdout);
                } else {
                    System.out.println(output);
                }

                continue;
            }

            if (command.equals("pwd")) {
                String cwd = System.getProperty("user.dir");

                if (stdoutFile != null) {
                    writeToFile(stdoutFile, cwd + "\n", appendStdout);
                } else {
                    System.out.println(cwd);
                }

                continue;
            }

            if (command.equals("cd")) {
                String target = commandArgs.size() > 1
                        ? commandArgs.get(1)
                        : "~";

                if (target.equals("~")) {
                    target = System.getenv("HOME");

                    if (target == null) {
                        target = System.getProperty("user.home");
                    }
                }

                File dir = new File(target);

                if (!dir.isAbsolute()) {
                    dir = new File(System.getProperty("user.dir"), target);
                }

                try {
                    String path = dir.getCanonicalPath();

                    if (new File(path).isDirectory()) {
                        System.setProperty("user.dir", path);
                    } else {
                        System.out.println("cd: " + target + ": No such file or directory");
                    }
                } catch (IOException e) {
                    System.out.println("cd: " + target + ": No such file or directory");
                }

                continue;
            }

            if (command.equals("type")) {
                if (commandArgs.size() < 2) {
                    continue;
                }

                String target = commandArgs.get(1);

                if (isBuiltin(target)) {
                    System.out.println(target + " is a shell builtin");
                } else {
                    String path = findInPath(target);

                    if (path != null) {
                        System.out.println(target + " is " + path);
                    } else {
                        System.out.println(target + ": not found");
                    }
                }

                continue;
            }

            if (command.equals("jobs")) {
                for (Job job : jobsList) {
                    if (job.process.isAlive()) {
                        System.out.printf(
                                "[%d]+  %-24s%s%n",
                                job.id,
                                "Running",
                                job.command + " &"
                        );
                    }
                }

                continue;
            }

            String executable = findInPath(command);

            if (executable == null) {
                File local = new File(command);

                if (local.isAbsolute() && local.exists()) {
                    executable = local.getAbsolutePath();
                } else {
                    File relative = new File(System.getProperty("user.dir"), command);

                    if (relative.exists()) {
                        executable = relative.getAbsolutePath();
                    }
                }
            }

            if (executable == null) {
                System.out.println(command + ": command not found");
                continue;
            }

            commandArgs.set(0, executable);

            ProcessBuilder pb = new ProcessBuilder(commandArgs);
            pb.directory(new File(System.getProperty("user.dir")));

            if (isBackground) {
                pb.inheritIO();
            } else {
                if (stdoutFile != null) {
                    pb.redirectOutput(
                            appendStdout
                                    ? ProcessBuilder.Redirect.appendTo(new File(stdoutFile))
                                    : ProcessBuilder.Redirect.to(new File(stdoutFile))
                    );
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }

                if (stderrFile != null) {
                    pb.redirectError(
                            appendStderr
                                    ? ProcessBuilder.Redirect.appendTo(new File(stderrFile))
                                    : ProcessBuilder.Redirect.to(new File(stderrFile))
                    );
                } else {
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }
            }

            try {
                Process process = pb.start();

                if (isBackground) {
                    int jobId = nextJobId++;

                    jobsList.add(
                            new Job(
                                    jobId,
                                    process.pid(),
                                    String.join(" ", commandArgs.subList(1, commandArgs.size())),
                                    process
                            )
                    );

                    System.out.printf("[%d] %d%n", jobId, process.pid());
                } else {
                    process.waitFor();
                }

            } catch (IOException e) {
                System.out.println(command + ": command not found");
            }
        }

        scanner.close();
    }

    private static boolean isBuiltin(String command) {
        return command.equals("exit")
                || command.equals("echo")
                || command.equals("pwd")
                || command.equals("type")
                || command.equals("cd")
                || command.equals("jobs");
    }

    private static List<String> parseArguments(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (inDoubleQuotes) {
                if (c == '\\' && i + 1 < input.length()) {
                    char next = input.charAt(i + 1);

                    if (next == '$' || next == '`' || next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                    } else {
                        current.append(c);
                    }
                } else if (c == '"') {
                    inDoubleQuotes = false;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '\\' && i + 1 < input.length()) {
                    current.append(input.charAt(++i));
                } else if (c == '\'') {
                    inSingleQuotes = true;
                } else if (c == '"') {
                    inDoubleQuotes = true;
                } else if (Character.isWhitespace(c)) {
                    if (current.length() > 0) {
                        args.add(current.toString());
                        current.setLength(0);
                    }
                } else {
                    current.append(c);
                }
            }
        }

        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args;
    }

    private static String findInPath(String command) {
        String path = System.getenv("PATH");

        if (path == null) {
            return null;
        }

        for (String dir : path.split(File.pathSeparator)) {
            File file = new File(dir, command);

            if (file.exists() && !file.isDirectory()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    private static void writeToFile(String filename, String content, boolean append) {
        try {
            if (append) {
                Files.writeString(
                        new File(filename).toPath(),
                        content,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } else {
                Files.writeString(
                        new File(filename).toPath(),
                        content,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );
            }
        } catch (IOException ignored) {
        }
    }
}