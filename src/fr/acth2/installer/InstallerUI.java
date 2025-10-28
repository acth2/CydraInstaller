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
    private static final String BLUE = "\u001B[34m";

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
                "",
                "Press Enter to continue..."
        };
        showContentBox(content);

        System.out.println();
        String centeredPrompt = centerText("> ", terminalWidth);
        System.out.print(centeredPrompt);
        scanner.nextLine();
    }

    public void showSection(String sectionName) {
        clearAndShowFullScreen();

        String[] content = {sectionName};
        showContentBox(content);
    }

    public void showMessage(String message) {
        clearAndShowFullScreen();

        String[] content = {message};
        showContentBox(content);
        waitForEnter();
    }

    public void showError(String error) {
        clearAndShowFullScreen();

        String[] content = {"ERROR: " + error};
        showContentBox(content);
        waitForEnter();
    }

    public boolean confirmAction(String message) {
        clearAndShowFullScreen();

        String[] content = {message};
        showContentBox(content);

        System.out.println();
        String centeredPrompt = centerText("(y/n): ", terminalWidth);
        System.out.print(centeredPrompt);
        String response = scanner.nextLine().trim().toLowerCase();
        return response.equals("y") || response.equals("yes");
    }

    public String getInput(String prompt) {
        clearAndShowFullScreen();

        String[] content = {prompt};
        showContentBox(content);

        System.out.println();
        String centeredPrompt = centerText("> ", terminalWidth);
        System.out.print(centeredPrompt);
        return scanner.nextLine().trim();
    }

    public String getPassword(String prompt) {
        clearAndShowFullScreen();

        String[] content = {prompt};
        showContentBox(content);

        System.out.println();
        String centeredPrompt = centerText("> ", terminalWidth);
        System.out.print(centeredPrompt);

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
            String centeredPrompt = centerText("Select option (1-" + options.size() + "): ", terminalWidth);
            System.out.print(centeredPrompt);
            try {
                int choice = Integer.parseInt(scanner.nextLine());
                if (choice >= 1 && choice <= options.size()) {
                    return options.get(choice - 1);
                }
            } catch (NumberFormatException e) {
            }
            clearAndShowFullScreen();
            String[] errorContent = {"Invalid selection. Please try again."};
            showContentBox(errorContent);
        }
    }

    public void waitForEnter() {
        clearAndShowFullScreen();

        String[] content = {"Press Enter to continue..."};
        showContentBox(content);

        System.out.println();
        String centeredPrompt = centerText("> ", terminalWidth);
        System.out.print(centeredPrompt);
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
        System.out.println("\n\n");
    }

    private void showHeader() {
        updateTerminalSize();
        String headerLine = " ".repeat(terminalWidth);
        System.out.println(BLUE_BG + WHITE_BOLD + headerLine + RESET);
        System.out.println(BLUE_BG + WHITE_BOLD + centerText("CydraLite Installer", terminalWidth) + RESET);
        System.out.println(BLUE_BG + WHITE_BOLD + headerLine + RESET);
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
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        return bar.toString();
    }

    private void showContentBox(String[] contentLines) {
        int maxLineLength = 0;
        for (String line : contentLines) {
            if (line.length() > maxLineLength) {
                maxLineLength = line.length();
            }
        }

        int boxWidth = Math.min(terminalWidth - 4, maxLineLength + 6);
        String border = "┌" + "─".repeat(boxWidth - 2) + "┐";

        System.out.println(centerText(border, terminalWidth));

        for (String line : contentLines) {
            String paddedLine = "│" + centerText(line, boxWidth - 2) + "│";
            System.out.println(centerText(paddedLine, terminalWidth));
        }

        String bottom = "└" + "─".repeat(boxWidth - 2) + "┘";
        System.out.println(centerText(bottom, terminalWidth));
    }

    private String centerText(String text, int width) {
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
        } catch (Exception e) {
        }

        try {
            Process process = Runtime.getRuntime().exec(new String[]{"tput", "cols"});
            process.waitFor();
            InputStreamReader reader = new InputStreamReader(process.getInputStream());
            char[] buffer = new char[8];
            int bytesRead = reader.read(buffer);
            if (bytesRead > 0) {
                String result = new String(buffer, 0, bytesRead).trim();
                terminalWidth = Integer.parseInt(result);
            }
        } catch (Exception e) {
            terminalWidth = 80;
        }

        terminalHeight = 24;
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}