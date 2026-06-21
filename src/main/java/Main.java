import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    
    // Data structure to track background jobs
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

            // Check if command should run in background
            boolean isBackground = false;
            if (input.endsWith("&")) {
                isBackground = true;
                input = input.substring(0, input.length() - 1).trim();
                if (input.isEmpty()) {
                    continue;
                }
            }

            // Parse arguments handling quotes and backslashes
            List<String> parsedArgs = parseArguments(input);
            if (parsedArgs.isEmpty()) {
                continue;
            }

            String command = parsedArgs.get(0);

            // Redirection processing
            String stdoutFile = null;
            String stderrFile = null;
            boolean appendStdout = false;
            boolean appendStderr = false;

            List<String> commandArgs = new ArrayList<>();
            for (int i = 0; i < parsedArgs.size(); i++) {
                String arg = parsedArgs.get(i);
                if (arg.equals(">") || arg.equals("1>")) {
                    if (i + 1 < parsedArgs.size()) {
                        stdoutFile = parsedArgs.get(++i);
                    }
                } else if (arg.equals(">>") || arg.equals("1>>")) {
                    if (i + 1 < parsedArgs.size()) {
                        stdoutFile = parsedArgs.get(++i);
                        appendStdout = true;
                    }
                } else if (arg.equals("2>")) {
                    if (i + 1 < parsedArgs.size()) {
                        stderrFile = parsedArgs.get(++i);
                    }
                } else if (arg.equals("2>>")) {
                    if (i + 1 < parsedArgs.size()) {
                        stderrFile = parsedArgs.get(++i);
                        appendStderr = true;
                    }
                } else {
                    commandArgs.add(arg);
                }
            }

            if (commandArgs.isEmpty()) {
                continue;
            }
            command = commandArgs.get(0);

            // Built-in handlers
            if (command.equals("exit")) {
                String exitCode = (commandArgs.size() > 1) ? commandArgs.get(1) : "0";
                System.exit(Integer.parseInt(exitCode));
            } else if (command.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < commandArgs.size(); i++) {
                    sb.append(commandArgs.get(i));
                    if (i < commandArgs.size() - 1) {
                        sb.append(" ");
                    }
                }
                String output = sb.toString();
                if (stdoutFile != null) {
                    writeToFile(stdoutFile, output + "\n", appendStdout);
                } else {
                    System.out.println(output);
                }
                continue;
            } else if (command.equals("pwd")) {
                String currentDir = System.getProperty("user.dir");
                if (stdoutFile != null) {
                    writeToFile(stdoutFile, currentDir + "\n", appendStdout);
                } else {
                    System.out.println(currentDir);
                }
                continue;
            } else if (command.equals("cd")) {
                String targetDir = (commandArgs.size() > 1) ? commandArgs.get(1) : "~";
                if (targetDir.equals("~")) {
                    targetDir = System.getenv("HOME");
                    if (targetDir == null) {
                        targetDir = System.getProperty("user.home");
                    }
                }
                File file = new File(targetDir);
                if (!file.isAbsolute()) {
                    file = new File(System.getProperty("user.dir"), targetDir);
                }
                try {
                    String canonicalPath = file.getCanonicalPath();
                    File actualFile = new File(canonicalPath);
                    if (actualFile.exists() && actualFile.isDirectory()) {
                        System.setProperty("user.dir", canonicalPath);
                    } else {
                        System.out.println("cd: " + targetDir + ": No such file or directory");
                    }
                } catch (IOException e) {
                    System.out.println("cd: " + targetDir + ": No such file or directory");
                }
                continue;
            } else if (command.equals("type")) {
                if (commandArgs.size() < 2) {
                    continue;
                }
                String cmdToType = commandArgs.get(1);
                if (cmdToType.equals("exit") || cmdToType.equals("echo") || cmdToType.equals("pwd") || cmdToType.equals("type") || cmdToType.equals("cd") || cmdToType.equals("jobs")) {
                    System.out.println(cmdToType + " is a shell builtin");
                } else {
                    String path = findInPath(cmdToType);
                    if (path != null) {
                        System.out.println(cmdToType + " is " + path);
                    } else {
                        System.out.println(cmdToType + " not found");
                    }
                }
                continue;
            } else if (command.equals("jobs")) {
                int totalJobs = jobsList.size();
                for (int i = 0; i < totalJobs; i++) {
                    Job j = jobsList.get(i);
                    char marker = ' ';
                    if (i == totalJobs - 1) {
                        marker = '+';
                    } else if (i == totalJobs - 2) {
                        marker = '-';
                    }
                    System.out.printf("[%d]%c  Running                 %s &\n", j.id, marker, j.command);
                }
                continue;
            }

            // Execute External Programs
            String executablePath = findInPath(command);
            if (executablePath == null) {
                File localFile = new File(command);
                if (localFile.isAbsolute() && localFile.exists()) {
                    executablePath = command;
                } else {
                    File relativeFile = new File(System.getProperty("user.dir"), command);
                    if (relativeFile.exists()) {
                        executablePath = relativeFile.getAbsolutePath();
                    }
                }
            }

            if (executablePath != null) {
                commandArgs.set(0, executablePath);
                try {
                    ProcessBuilder pb = new ProcessBuilder(commandArgs);
                    pb.directory(new File(System.getProperty("user.dir")));

                    if (stdoutFile != null) {
                        pb.redirectOutput(appendStdout ? ProcessBuilder.Redirect.appendTo(new File(stdoutFile)) : ProcessBuilder.Redirect.to(new File(stdoutFile)));
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (stderrFile != null) {
                        pb.redirectError(appendStderr ? ProcessBuilder.Redirect.appendTo(new File(stderrFile)) : ProcessBuilder.Redirect.to(new File(stderrFile)));
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    Process process = pb.start();

                    if (isBackground) {
                        int currentJobId = nextJobId++;
                        long pid = process.pid();
                        String jobCommandStr = input;
                        Job newJob = new Job(currentJobId, pid, jobCommandStr, process);
                        jobsList.add(newJob);
                        System.out.printf("[%d] %d\n", currentJobId, pid);
                    } else {
                        process.waitFor();
                    }
                } catch (IOException | InterruptedException e) {
                    System.out.println(command + ": command not found");
                }
            } else {
                System.out.println(command + ": command not found");
            }
        }
        scanner.close();
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
                if (c == '\\') {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '$' || next == '`' || next == '"' || next == '\\' || next == '\n') {
                            current.append(next);
                            i++;
                        } else {
                            current.append(c);
                        }
                    } else {
                        current.append(c);
                    }
                } else if (c == '"') {
                    inDoubleQuotes = false;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '\\') {
                    if (i + 1 < input.length()) {
                        current.append(input.charAt(i + 1));
                        i++;
                    }
                } workspace: {
                } if (c == '\'') {
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
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        String separator = File.pathSeparator;
        String[] dirs = pathEnv.split(separator);
        for (String dir : dirs) {
            File file = new File(dir, command);
            if (file.exists() && !file.isDirectory()) {
                return file.getAbsolutePath();
            }
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                File exeFile = new File(dir, command + ".exe");
                if (exeFile.exists() && !exeFile.isDirectory()) {
                    return exeFile.getAbsolutePath();
                }
                File batFile = new File(dir, command + ".bat");
                if (batFile.exists() && !batFile.isDirectory()) {
                    return batFile.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private static void writeToFile(String filename, String content, boolean append) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(filename);
            if (append) {
                java.nio.file.Files.writeString(path, content, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } else {
                java.nio.file.Files.writeString(path, content, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE);
            }
        } catch (IOException e) {
            // Silently handle redirection errors
        }
    }
}