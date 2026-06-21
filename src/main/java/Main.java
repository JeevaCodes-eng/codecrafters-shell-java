import java.io.File;
import java.util.Scanner;

public class Main {

    private static boolean isBuiltin(String command) {
        return command.equals("echo")
                || command.equals("exit")
                || command.equals("type");
    }

    public static void main(String[] args) {
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
                    continue;
                }

                String path = System.getenv("PATH");

                if (path != null) {
                    String[] directories = path.split(File.pathSeparator);

                    for (String directory : directories) {
                        File file = new File(directory, command);

                        if (file.exists() && file.canExecute()) {
                            System.out.println(command + " is " + file.getAbsolutePath());
                            command = null;
                            break;
                        }
                    }
                }

                if (command != null) {
                    System.out.println(command + ": not found");
                }

                continue;
            }

            System.out.println(input + ": command not found");
        }

        scanner.close();
    }
}