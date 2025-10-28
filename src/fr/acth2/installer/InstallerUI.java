package fr.acth2.installer;

import java.io.BufferedReader;
import java.io.Console;
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

    public InstallerUI() {
        this.scanner = new Scanner(System.in);
        this.terminalWidth = getTerminalWidth();
    }

    public void showWelcome() {
        clearScreen();
        showHeader();
        showProgressBar();
        System.out.println("\n\n");
        System.out.println("          CydraLite");
        System.out.println("\n");
        System.out.println("text");
        System.out.println("text");
        System.out.println("text");
        System.out.println("text");
        System.out.println("\n\n");
        showNavigation(false, true);
    }

    public void showSection(String sectionName) {
        clearScreen();
        showHeader();
        showProgressBar();
        System.out.println("\n\n");
        System.out.println("          " + sectionName);
        System.out.println("\n");
    }

    public void showMessage(String message) {
        System.out.println(message);
    }

    public void showError(String error) {
        System.out.println("ERROR: " + error);
    }

    public boolean confirmAction(String message) {
        System.out.println(message);
        System.out.print("(y/n): ");
        String response = scanner.nextLine().trim().toLowerCase();
        return response.equals("y") || response.equals("yes");
    }

    public String getInput(String prompt) {
        System.out.print(prompt + ": ");
        return scanner.nextLine().trim();
    }

    public String getPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            char[] passwordChars = console.readPassword(prompt + ": ");
            return new String(passwordChars);
        } else {
            System.out.print(prompt + ": ");
            return scanner.nextLine().trim();
        }
    }

    public String selectFromList(String title, List<String> options) {
        System.out.println(title + ":");
        for (int i = 0; i < options.size(); i++) {
            System.out.println((i + 1) + ". " + options.get(i));
        }

        while (true) {
            try {
                System.out.print("Select option (1-" + options.size() + "): ");
                int choice = Integer.parseInt(scanner.nextLine());
                if (choice >= 1 && choice <= options.size()) {
                    return options.get(choice - 1);
                }
            } catch (NumberFormatException e) {
            }
            System.out.println("Invalid selection. Please try again.");
        }
    }

    public void waitForEnter() {
        System.out.println("Press Enter to continue...");
        scanner.nextLine();
    }

    public void updateProgress(int section) {
        this.currentSection = section;
        this.terminalWidth = getTerminalWidth();
    }

    private void showHeader() {
        String headerLine = getSpaces(terminalWidth);
        System.out.println(BLUE_BG + WHITE_BOLD + headerLine + RESET);
        System.out.println(BLUE_BG + WHITE_BOLD + centerText("CydraLite Installer", terminalWidth) + RESET);
        System.out.println(BLUE_BG + WHITE_BOLD + headerLine + RESET);
    }

    private void showProgressBar() {
        if (terminalWidth < 40) {
            showCompactProgressBar();
        } else {
            showFullProgressBar();
        }
    }

    private void showFullProgressBar() {
        System.out.print("\n" + BLUE + "Progress: [" + RESET);
        int filledSegments = (currentSection * 10) / totalSections;
        int barWidth = Math.min(20, terminalWidth - 15);

        for (int i = 0; i < 10; i++) {
            if (i < filledSegments) {
                System.out.print(BLUE + "█" + RESET);
            } else {
                System.out.print("░");
            }
        }
        System.out.println(BLUE + "] " + (currentSection * 100 / totalSections) + "%" + RESET);
    }

    private void showCompactProgressBar() {
        int percentage = (currentSection * 100 / totalSections);
        System.out.println("\n" + BLUE + "Progress: " + percentage + "%" + RESET);
    }

    private String centerText(String text, int width) {
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        int padding = (width - text.length()) / 2;
        return getSpaces(padding) + text + getSpaces(width - text.length() - padding);
    }

    private String getSpaces(int count) {
        return " ".repeat(Math.max(0, count));
    }

    private int getTerminalWidth() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("linux") || os.contains("mac") || os.contains("unix")) {
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "tput cols 2>/dev/null || echo 80"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line = reader.readLine();
                return Integer.parseInt(line.trim());
            }
        } catch (Exception e) {
        }
        return 80;
    }

    private void showNavigation(boolean showBack, boolean showNext) {
        System.out.println("\n");
        if (showBack) {
            System.out.print("[retour] ");
        }
        if (showNext) {
            System.out.print("[suivant]");
        }
        System.out.println();
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}