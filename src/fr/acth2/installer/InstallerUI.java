package fr.acth2.installer;

import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Scanner;

public class InstallerUI {
    private static final String BLUE_BG = "\u001B[44m";
    private static final String WHITE_BOLD = "\u001B[1;37m";
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\033[0;31m";
    private static final String BLUE = "\u001B[34m";
    private static final String GREEN = "\u001B[32m";

    private Scanner scanner;
    private int totalSections = 8;
    private int currentSection = 0;
    private int terminalWidth;
    private int terminalHeight;

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
        clearAndShowFullScreen();

        String[] content = {sectionName};
        showContentBox(content);
    }

    public void showMessage(String message) {
        clearAndShowFullScreen();

        String[] lines = splitMessage(message, Math.min(terminalWidth - 10, 70));
        showContentBox(lines);
        waitForEnter();
    }

    public void showInlineMessage(String message) {
        System.out.println();
        System.out.println(GREEN + "✓ " + message + RESET);
        System.out.println();
    }

    public void showError(String error) {
        CydraInstaller.error = true;
        clearAndShowFullScreen();
        updateProgress(8);

        String[] lines = splitMessage("ERROR: " + error, Math.min(terminalWidth - 10, 70) + 19);
        showContentBox(lines);
        waitForEnter();
    }

    public boolean confirmAction(String message) {
        clearAndShowFullScreen();

        String[] lines = splitMessage(message, Math.min(terminalWidth - 10, 70));
        showContentBox(lines);

        System.out.println();
        System.out.print("(y/n): ");
        String response = scanner.nextLine().trim().toLowerCase();
        return response.equals("y") || response.equals("yes");
    }

    public String getInput(String prompt) {
        clearAndShowFullScreen();

        String[] lines = splitMessage(prompt, Math.min(terminalWidth - 10, 70));
        showContentBox(lines);

        System.out.println();
        System.out.print("> ");
        String input = scanner.nextLine().trim();

        if (!input.isEmpty()) {
            showInlineMessage("Set to: " + input);
        }

        return input;
    }

    public String getInputWithoutConfirmation(String prompt) {
        clearAndShowFullScreen();

        String[] lines = splitMessage(prompt, Math.min(terminalWidth - 10, 70));
        showContentBox(lines);

        System.out.println();
        System.out.print("> ");
        return scanner.nextLine().trim();
    }

    public String getPassword(String prompt) {
        clearAndShowFullScreen();

        String[] lines = splitMessage(prompt, Math.min(terminalWidth - 10, 70));
        showContentBox(lines);

        System.out.println();
        System.out.print("> ");

        Console console = System.console();
        if (console != null) {
            char[] passwordChars = console.readPassword();
            return new String(passwordChars);
        } else {
            return scanner.nextLine().trim();
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
            System.out.println("Invalid selection. Please try again.");
        }
    }

    public void waitForEnter() {
        System.out.println();
        System.out.print("Press Enter to continue...");
        scanner.nextLine();
    }

    public void updateProgress(int section) {
        this.currentSection = section;
        updateTerminalSize();
    }

    private void clearAndShowFullScreen() {
        clearScreen();
        showHeader();
        showProgressBar();
        System.out.println("\n");
    }

    private void showHeader() {
        updateTerminalSize();
        String headerLine = " ".repeat(terminalWidth);
        if (!CydraInstaller.error) {
            System.out.println(BLUE_BG + WHITE_BOLD + headerLine + RESET);
            System.out.println(BLUE_BG + WHITE_BOLD + centerText("CydraLite Installer", terminalWidth) + RESET);
            System.out.println(BLUE_BG + WHITE_BOLD + headerLine + RESET);
        } else {
            System.out.println(RESET + WHITE_BOLD + headerLine + RESET);
            System.out.println(RESET + WHITE_BOLD + centerText("CydraLite Installer encountered an error.", terminalWidth) + RESET);
            System.out.println(RESET + WHITE_BOLD + headerLine + RESET);
        }
    }

    private void showProgressBar() {
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

    private void showContentBox(String[] contentLines) {
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

    private String[] splitMessage(String message, int maxWidth) {
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

    private String centerText(String text, int width) {
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

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}