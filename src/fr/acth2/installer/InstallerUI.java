package fr.acth2.installer;

import java.io.Console;
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

    public InstallerUI() {
        this.scanner = new Scanner(System.in);
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
    }

    private void showHeader() {
        System.out.println(BLUE_BG + WHITE_BOLD + "                                                    " + RESET);
        System.out.println(BLUE_BG + WHITE_BOLD + "                  CydraLite Installer                " + RESET);
        System.out.println(BLUE_BG + WHITE_BOLD + "                                                    " + RESET);
    }

    private void showProgressBar() {
        System.out.print("\n" + BLUE + "Progress: [" + RESET);
        int filledSegments = (currentSection * 10) / totalSections;

        for (int i = 0; i < 10; i++) {
            if (i < filledSegments) {
                System.out.print(BLUE + "█" + RESET);
            } else {
                System.out.print("░");
            }
        }
        System.out.println(BLUE + "] " + (currentSection * 100 / totalSections) + "%" + RESET);
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