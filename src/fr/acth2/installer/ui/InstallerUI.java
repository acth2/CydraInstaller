package fr.acth2.installer.ui;

import fr.acth2.installer.CydraInstaller;

import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

public class InstallerUI {
    private static final String BLUE_BG = "\u001B[44m";
    private static final String WHITE_BOLD = "\u001B[1;37m";
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\033[0;31m";
    private static final String BLUE = "\u001B[34m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";

    public static Scanner scanner;
    private int totalSections = 8;
    private int currentSection = 0;
    private int terminalWidth;
    private int terminalHeight;
    private String currentSectionName;

    public InstallerUI() {
        this.scanner = new Scanner(System.in);
        updateTerminalSize();
    }

    public void showWelcome() {
        clearAndShowFullScreen();

        String[] content = {
                "CydraLite",
                "",
                "Welcome to CydraLite Installer",
                "This will guide you through the installation",
                ""
        };

        showContentBox(content);
        waitForEnter();
    }

    public void showSection(String sectionName) {
        currentSectionName = sectionName;
        clearAndShowFullScreen();

        String[] content = {sectionName};
        //showContentBox(content);
    }

    public void showMessage(String message) {
        clearAndShowFullScreen();

        String[] lines = splitMessage(message, Math.min(terminalWidth - 10, 70));
        showContentBox(lines);
        waitForEnter();
    }

    public void showContentBoxNoClear(String[] contentLines) {
        if (contentLines == null || contentLines.length == 0) {
            return;
        }

        int maxLineLength = 0;
        for (String line : contentLines) {
            if (line != null && line.length() > maxLineLength) {
                maxLineLength = line.length();
            }
        }

        int boxWidth = Math.min(terminalWidth - 4, Math.max(maxLineLength + 6, 20));
        String border = "#" + "#".repeat(boxWidth - 2) + "#";

        System.out.println(centerText(border, terminalWidth));

        for (String line : contentLines) {
            if (line == null) continue;
            String paddedLine = "|" + centerText(line, boxWidth - 2) + "|";
            System.out.println(centerText(paddedLine, terminalWidth));
        }

        String bottom = "#" + "#".repeat(boxWidth - 2) + "#";
        System.out.println(centerText(bottom, terminalWidth));

        System.out.println();
    }

    public void showMessage(String message, boolean informative) {
        if (!informative) {
            clearAndShowFullScreen();
        }

        String[] lines = splitMessage(message, Math.min(terminalWidth - 10, 70));
        showContentBox(lines);

        if (!informative) {
            waitForEnter();
        }
    }

    public void showInlineMessage(String message) {
        System.out.println();
        System.out.println(GREEN + message + RESET);
        System.out.println();
    }

    public void showWarning(String warning) {
        System.out.println();
        System.out.println(YELLOW + "/!\\ " + warning + RESET);
        System.out.println();
    }

    public void showError(String error) {
        CydraInstaller.error = true;
        clearAndShowFullScreen();
        updateProgress(8);

        String[] lines = splitMessage("ERROR: " + error, Math.min(terminalWidth - 10, 70) + 19);
        showContentBox(lines);
        waitAndKill(2500);
    }

    public boolean confirmAction(String message) {
        clearAndShowFullScreen();

        String[] lines = splitMessage(message, Math.min(terminalWidth - 10, 70));
        showContentBox(lines);

        while (true) {
            System.out.println();
            System.out.print("(y/n): ");
            String response = scanner.nextLine().trim().toLowerCase();
            if (response.equals("y") || response.equals("yes")) {
                return true;
            } else if (response.equals("n") || response.equals("no")) {
                return false;
            }
            System.out.println(RED + "Please enter 'y' for yes or 'n' for no." + RESET);
        }
    }

    public String getInput(String prompt, String validationRegex, String errorMessage) {
        while (true) {
            clearAndShowFullScreen();

            String[] lines = splitMessage(prompt, Math.min(terminalWidth - 10, 70));
            showContentBox(lines);

            System.out.println();
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                showWarning("Input cannot be empty. Please try again.");
                continue;
            }

            if (validationRegex == null || input.matches(validationRegex)) {
                if (!input.isEmpty()) {
                    showInlineMessage("Set to: " + input);
                }
                return input;
            } else {
                showWarning(errorMessage);
            }
        }
    }

    public String getInputWithoutConfirmation(String prompt, String validationRegex, String errorMessage) {
        while (true) {
            clearAndShowFullScreen();

            String[] lines = splitMessage(prompt, Math.min(terminalWidth - 10, 70));
            showContentBox(lines);

            System.out.println();
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                showWarning("Input cannot be empty. Please try again.");
                continue;
            }

            if (validationRegex == null || input.matches(validationRegex)) {
                return input;
            } else {
                showWarning(errorMessage);
            }
        }
    }

    public String getPassword(String prompt) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("No console available. Please run this program from a terminal.");
        }

        while (true) {
            clearAndShowFullScreen();

            String[] lines = splitMessage(prompt, Math.min(terminalWidth - 10, 70));
            showContentBox(lines);

            char[] pwdChars = console.readPassword("> ");
            String password = new String(pwdChars).trim();

            if (password.length() < 4) {
                showWarning("Password must be at least 4 characters long. Please try again.");
                continue;
            }

            String[] confirmLines = splitMessage("Please confirm your password", Math.min(terminalWidth - 10, 70));
            showContentBox(confirmLines);

            char[] confirmPwdChars = console.readPassword("> ");
            String confirmPassword = new String(confirmPwdChars).trim();

            if (password.equals(confirmPassword)) {
                return password;
            } else {
                showWarning("Passwords do not match. Please try again.");
            }
        }
    }


    public String selectFromList(String title, List<String> options) {
        clearAndShowFullScreen();

        String[] content = new String[options.size() + 1];
        content[0] = title + ":";
        for (int i = 0; i < options.size(); i++) {
            content[i + 1] = (i + 1) + ". " + options.get(i);
        }
        showContentBox(content);

        while (true) {
            System.out.println();
            System.out.print("Select option (1-" + options.size() + "): ");
            try {
                int choice = Integer.parseInt(scanner.nextLine());
                if (choice >= 1 && choice <= options.size()) {
                    String selected = options.get(choice - 1);
                    showInlineMessage("Selected: " + selected);
                    return selected;
                }
            } catch (NumberFormatException e) {}
            System.out.println(RED + "Invalid selection. Please enter a number between 1 and " + options.size() + "." + RESET);
        }
    }

    public void waitForEnter() {
        System.out.println();
        System.out.print("Press Enter to continue...");

        try {
            while (System.in.available() > 0) {
                System.in.read();
            }
        } catch (IOException ignored) {}

        scanner.nextLine();
    }

    public void waitAndKill(int milliseconds) {
        int seconds = milliseconds / 1000;
        System.out.println();
        System.out.println("Program will end in " + seconds + " seconds");
        try { Thread.sleep(milliseconds); } catch (InterruptedException e) { throw new RuntimeException(e); }
        System.exit(0);
    }

    public void updateProgress(int section) {
        this.currentSection = section;
        updateTerminalSize();
    }

    public void clearAndShowFullScreen() {
        clearScreen();
        if (isEfiSystem()) {
            showHeader();
            showProgressBar();
        } else if (CydraInstaller.isSystemInstalling()) {
            showProgressBar();
        }
        System.out.println("\n");
    }

    void showHeader() {
        updateTerminalSize();
        String headerLine = " ".repeat(terminalWidth);
        if (!CydraInstaller.error) {
            if (currentSectionName == null) {
                System.out.println(BLUE_BG + WHITE_BOLD + headerLine + RESET);
                System.out.println(BLUE_BG + WHITE_BOLD + centerText("CydraLite Installer", terminalWidth) + RESET);
                System.out.println(BLUE_BG + WHITE_BOLD + headerLine + RESET);
            } else {
                System.out.println(BLUE_BG + WHITE_BOLD + headerLine + RESET);
                System.out.println(BLUE_BG + WHITE_BOLD + centerText("CydraLite Installer - " + currentSectionName, terminalWidth) + RESET);
                System.out.println(BLUE_BG + WHITE_BOLD + headerLine + RESET);
            }
        } else {
            System.out.println(RED + WHITE_BOLD + headerLine + RESET);
            System.out.println(RED + WHITE_BOLD + centerText("CydraLite Installer encountered an error.", terminalWidth) + RESET);
            System.out.println(RED + WHITE_BOLD + headerLine + RESET);
        }
    }

    void showProgressBar() {
        System.out.println();
        int percentage = (currentSection * 100 / totalSections);

        if (terminalWidth >= 50) {
            String progressText = "Progress: [" + getProgressBar(20) + "] " + percentage + "%";
            System.out.println(centerText(progressText, terminalWidth));
        } else if (terminalWidth >= 30) {
            String progressText = "Progress: " + getProgressBar(10) + " " + percentage + "%";
            System.out.println(centerText(progressText, terminalWidth));
        } else {
            System.out.println(centerText("Progress: " + percentage + "%", terminalWidth));
        }
    }

    private String getProgressBar(int length) {
        int filled = (currentSection * length) / totalSections;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append("#");
            } else {
                bar.append(" ");
            }
        }
        return bar.toString();
    }

    public void showContentBox(String[] contentLines) {
        if (contentLines == null || contentLines.length == 0) {
            return;
        }

        int maxLineLength = 0;
        for (String line : contentLines) {
            if (line != null && line.length() > maxLineLength) {
                maxLineLength = line.length();
            }
        }

        int boxWidth = Math.min(terminalWidth - 4, Math.max(maxLineLength + 6, 20));
        String border = "#" + "#".repeat(boxWidth - 2) + "#";

        System.out.println(centerText(border, terminalWidth));

        for (String line : contentLines) {
            if (line == null) continue;
            String paddedLine = "|" + centerText(line, boxWidth - 2) + "|";
            System.out.println(centerText(paddedLine, terminalWidth));
        }

        String bottom = "#" + "#".repeat(boxWidth - 2) + "#";
        System.out.println(centerText(bottom, terminalWidth));
    }

    public String[] splitMessage(String message, int maxWidth) {
        if (message == null || message.length() <= maxWidth) {
            return new String[]{message};
        }

        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String word : message.split(" ")) {
            if (currentLine.length() + word.length() + 1 > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }
            }
            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines.toArray(new String[0]);
    }

    public String centerText(String text, int width) {
        if (text == null) return " ".repeat(width);
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        int padding = (width - text.length()) / 2;
        return " ".repeat(padding) + text + " ".repeat(width - text.length() - padding);
    }

    private void updateTerminalSize() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "stty size </dev/tty"});
            process.waitFor();
            InputStreamReader reader = new InputStreamReader(process.getInputStream());
            char[] buffer = new char[16];
            int bytesRead = reader.read(buffer);

            if (bytesRead > 0) {
                String result = new String(buffer, 0, bytesRead).trim();
                String[] dimensions = result.split(" ");
                if (dimensions.length >= 2) {
                    terminalHeight = Integer.parseInt(dimensions[0]);
                    terminalWidth = Integer.parseInt(dimensions[1]);
                    return;
                }
            }
        } catch (Exception e) {}

        try {
            Process process = Runtime.getRuntime().exec(new String[]{"tput", "cols"});
            process.waitFor();
            InputStreamReader reader = new InputStreamReader(process.getInputStream());
            char[] buffer = new char[8];
            int bytesRead = reader.read(buffer);
            if (bytesRead > 0) {
                String result = new String(buffer, 0, bytesRead).trim();
                terminalWidth = Integer.parseInt(result);
            } else {
                terminalWidth = 80;
            }

            process = Runtime.getRuntime().exec(new String[]{"tput", "lines"});
            process.waitFor();
            reader = new InputStreamReader(process.getInputStream());
            buffer = new char[8];
            bytesRead = reader.read(buffer);
            if (bytesRead > 0) {
                String result = new String(buffer, 0, bytesRead).trim();
                terminalHeight = Integer.parseInt(result);
            } else {
                terminalHeight = 24;
            }
        } catch (Exception e) {
            terminalWidth = 80;
            terminalHeight = 24;
        }
    }

    public int getTerminalWidth() {
        return terminalWidth;
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private boolean isEfiSystem() {
        return Files.exists(Paths.get("/sys/firmware/efi"));
    }
}