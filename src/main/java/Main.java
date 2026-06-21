import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static Path currentDirectory =
            Paths.get(System.getProperty("user.dir"));

    private static boolean isBuiltin(String command) {
        return command.equals("echo")
                || command.equals("exit")
                || command.equals("type")
                || command.equals("pwd")
                || command.equals("cd");
    }

    private static String findExecutable(String command) {
        String path = System.getenv("PATH");

        if (path == null) {
            return null;
        }

        for (String directory : path.split(File.pathSeparator)) {
            File file = new File(directory, command);

            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    private static List<String> parseInput(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaping = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaping) {
                current.append(c);
                escaping = false;
                continue;
            }

            if (c == '\\' && !inSingleQuotes) {
                escaping = true;
                continue;
            }

            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine();
            List<String> parts = parseInput(input);

            if (parts.isEmpty()) {
                continue;
            }

            String stdoutFile = null;
            String stderrFile = null;
            List<String> commandParts = new ArrayList<>();

            for (int i = 0; i < parts.size(); i++) {
                String token = parts.get(i);

                if ((token.equals(">") || token.equals("1>")) && i + 1 < parts.size()) {
                    stdoutFile = parts.get(++i);
                } else if (token.equals("2>") && i + 1 < parts.size()) {
                    stderrFile = parts.get(++i);
                } else {
                    commandParts.add(token);
                }
            }

            parts = commandParts;

            if (parts.isEmpty()) {
                continue;
            }

            String command = parts.get(0);

            if (command.equals("exit")) {
                break;
            }

            if (command.equals("pwd")) {
                if (stderrFile != null) {
                    Files.writeString(Paths.get(stderrFile), "");
                }

                String output = currentDirectory.toString();

                if (stdoutFile != null) {
                    Files.writeString(Paths.get(stdoutFile), output + "\n");
                } else {
                    System.out.println(output);
                }

                continue;
            }

            if (command.equals("cd")) {
                String target = parts.get(1);

                Path newPath;

                if (target.equals("~")) {
                    newPath = Paths.get(System.getenv("HOME"));
                } else if (Paths.get(target).isAbsolute()) {
                    newPath = Paths.get(target);
                } else {
                    newPath = currentDirectory.resolve(target);
                }

                newPath = newPath.normalize();

                if (Files.isDirectory(newPath)) {
                    currentDirectory = newPath;

                    if (stderrFile != null) {
                        Files.writeString(Paths.get(stderrFile), "");
                    }
                } else {
                    String error = "cd: " + target + ": No such file or directory";

                    if (stderrFile != null) {
                        Files.writeString(Paths.get(stderrFile), error + "\n");
                    } else {
                        System.err.println(error);
                    }
                }

                continue;
            }

            if (command.equals("echo")) {
                if (stderrFile != null) {
                    Files.writeString(Paths.get(stderrFile), "");
                }

                String output = parts.size() > 1
                        ? String.join(" ", parts.subList(1, parts.size()))
                        : "";

                if (stdoutFile != null) {
                    Files.writeString(Paths.get(stdoutFile), output + "\n");
                } else {
                    System.out.println(output);
                }

                continue;
            }

            if (command.equals("type")) {
                if (stderrFile != null) {
                    Files.writeString(Paths.get(stderrFile), "");
                }

                String target = parts.get(1);
                String output;

                if (isBuiltin(target)) {
                    output = target + " is a shell builtin";
                } else {
                    String executable = findExecutable(target);

                    if (executable != null) {
                        output = target + " is " + executable;
                    } else {
                        output = target + ": not found";
                    }
                }

                if (stdoutFile != null) {
                    Files.writeString(Paths.get(stdoutFile), output + "\n");
                } else {
                    System.out.println(output);
                }

                continue;
            }

            String executable = findExecutable(command);

            if (executable != null) {
                ProcessBuilder processBuilder = new ProcessBuilder(parts);

                processBuilder.directory(currentDirectory.toFile());

                if (stdoutFile != null) {
                    processBuilder.redirectOutput(new File(stdoutFile));
                } else {
                    processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }

                if (stderrFile != null) {
                    processBuilder.redirectError(new File(stderrFile));
                } else {
                    processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
                }

                Process process = processBuilder.start();
                process.waitFor();
            } else {
                String error = command + ": command not found";

                if (stderrFile != null) {
                    Files.writeString(Paths.get(stderrFile), error + "\n");
                } else {
                    System.err.println(error);
                }
            }
        }

        scanner.close();
    }
}