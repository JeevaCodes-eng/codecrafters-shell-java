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

            String command = parts.get(0);

            if (command.equals("exit")) {
                break;
            }

            if (command.equals("pwd")) {
                System.out.println(currentDirectory);
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
                } else {
                    System.out.println("cd: " + target + ": No such file or directory");
                }

                continue;
            }

            if (command.equals("echo")) {
                if (parts.size() > 1) {
                    System.out.println(String.join(" ", parts.subList(1, parts.size())));
                } else {
                    System.out.println();
                }

                continue;
            }

            if (command.equals("type")) {
                String target = parts.get(1);

                if (isBuiltin(target)) {
                    System.out.println(target + " is a shell builtin");
                } else {
                    String executable = findExecutable(target);

                    if (executable != null) {
                        System.out.println(target + " is " + executable);
                    } else {
                        System.out.println(target + ": not found");
                    }
                }

                continue;
            }

            String executable = findExecutable(command);

            if (executable != null) {
                List<String> processArgs = new ArrayList<>(parts);
                processArgs.set(0, executable);

                ProcessBuilder processBuilder = new ProcessBuilder(processArgs);

                processBuilder.directory(currentDirectory.toFile());
                processBuilder.inheritIO();

                Process process = processBuilder.start();
                process.waitFor();
            } else {
                System.out.println(command + ": command not found");
            }
        }

        scanner.close();
    }
}