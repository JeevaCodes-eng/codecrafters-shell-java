import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

public class Main {

    private static boolean isBuiltin(String command) {
        return command.equals("echo")
                || command.equals("exit")
                || command.equals("type");
    }

    private static String findExecutable(String command) {
        String path = System.getenv("PATH");

        if (path == null) {
            return null;
        }

        String[] directories = path.split(File.pathSeparator);

        for (String directory : directories) {
            File file = new File(directory, command);

            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine();

            if (input.equals("exit")) {
                break;
            }

            if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
                continue;
            }

            if (input.startsWith("type ")) {
                String command = input.substring(5);

                if (isBuiltin(command)) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    String executable = findExecutable(command);

                    if (executable != null) {
                        System.out.println(command + " is " + executable);
                    } else {
                        System.out.println(command + ": not found");
                    }
                }

                continue;
            }

            String[] parts = input.split(" ");
            String executable = findExecutable(parts[0]);

            if (executable != null) {
                ProcessBuilder processBuilder =
                        new ProcessBuilder(Arrays.asList(parts));

                processBuilder.inheritIO();

                Process process = processBuilder.start();
                process.waitFor();
            } else {
                System.out.println(input + ": command not found");
            }
        }

        scanner.close();
    }
}