package fr.acth2.installer;

import fr.acth2.installer.ui.InstallerUI;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class CydraInstaller {
    private Scanner scanner;
    private InstallerUI ui;
    private String machineName;
    private String username;
    private String password;
    private String chosenPartition;
    private String efiPartition;
    private boolean isWireless;
    private String networkName;
    private String networkPassword;
    private String language;
    private String timezone;
    private String keyboardLayout;
    private boolean enableSwap;
    private int swapSize;
    private boolean enableSSH;
    private String rootPassword;
    public static boolean error = false;
    private static boolean isInstalling = false;

    //private static final String LANGUAGE_PATTERN = "^(fr|us|en|de|es|it)$";
    private static final String HOSTNAME_PATTERN = "^[a-zA-Z0-9][a-zA-Z0-9-]{0,62}$";
    private static final String USERNAME_PATTERN = "^[a-z_][a-z0-9_-]{0,31}$";
    //private static final String TIMEZONE_PATTERN = "^[A-Za-z]+/[A-Za-z_]+$";
    //private static final String KEYBOARD_PATTERN = "^(us|fr|de|es|it|uk)$";

    public CydraInstaller() {
        this.scanner = new Scanner(System.in);
        this.ui = new InstallerUI();
    }

    public static void main(String[] args) {
        CydraInstaller installer = new CydraInstaller();
        installer.run();
    }

    public void run() {
        try {
            checkRootPrivileges();
            ui.updateProgress(0);
            ui.showWelcome();
            showInformations();

            getUserInfos();
            ui.updateProgress(4);

            if (ui.confirmAction("The next step will erase the data on your PC. Continue?")) {
                performInstallation();
            }
        } catch (Exception e) {
            ui.showError("Installation failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    private void performInstallation() {
        try {
            Path mntInstall = Paths.get("/mnt/install");
            Files.createDirectories(mntInstall.getParent());

            Path mntEfi = Paths.get("/mnt/efi");
            Files.createDirectories(mntEfi.getParent());

            diskPartition();
            ui.updateProgress(5);

            if (ui.confirmAction("Everything setup, are you ready to begin installation?")) {
                isInstalling = true;

                installCydra();
                ui.updateProgress(7);

                createUserAccount();
                ui.updateProgress(8);

                systemConfiguration();
                ui.updateProgress(9);

                cleanLive();
                ui.updateProgress(10);

                ui.showMessage("The Installation is finished, thanks for using CydraLite !");
            }
            offerReboot();
        } catch (Exception e) {
            ui.showError("Error during installation: " + e.getMessage());
        }
    }

    private void checkRootPrivileges() {
        if (!System.getProperty("user.name").equals("root")) {
            ui.showError("This installer must be run as root!");
            System.exit(1);
        }
    }

    private void showInformations() {
        ui.showSection("INTRODUCTION");
        ui.clearAndShowFullScreen();

        String[] message1 = {"Licenses on: https://github.com/acth2/CydraProject/blob/main/LICENSE"};
        ui.showContentBoxNoClear(message1);

        String[] message2 = {"This is an open-source project: https://github.com/acth2/CydraInstaller/tree/master"};
        ui.showContentBoxNoClear(message2);

        String[] message3 = {"Thanks to the LFS & BLFS team for everything !"};
        ui.showContentBoxNoClear(message3);

        ui.waitForEnter();
    }

    private void getUserInfos() {
        ui.showSection("SYSTEM CONFIGURATION");

        String[] fields = {
                "Language: " + (language != null ? language : "Not set"),
                "Keyboard Layout: " + (keyboardLayout != null ? keyboardLayout : "Not set"),
                "Machine Name: " + (machineName != null ? machineName : "Not set"),
                "Username: " + (username != null ? username : "Not set"),
                "User Password: " + (password != null ? "********" : "Not set"),
                "Root Password: " + (rootPassword != null ? "********" : "Not set"),
                "Timezone: " + (timezone != null ? timezone : "Not set"),
                "Swap File: " + (enableSwap ? swapSize + "GB" : "Disabled"),
                "Wireless: " + (isWireless ? "Enabled" : "Disabled"),
                "SSH: " + (enableSSH ? "Enabled" : "Disabled")
        };

        String[] descriptions = {
                "System language for localization (fr/us/en/de/es/it)",
                "Keyboard layout for console and X11 (us/fr/de/es/it/uk)",
                "Hostname for the system (letters, numbers, hyphens, max 63 chars)",
                "Your login username (lowercase, numbers, hyphens, underscores, max 32 chars)",
                "Password for your user account (min 4 characters)",
                "Password for root administrator account (min 4 characters)",
                "System timezone for clock and time settings",
                "Swap file size for memory management (1GB/2GB/4GB/8GB)",
                "Enable wireless network connection",
                "Enable basic firewall protection",
                "Enable SSH remote access"
        };

        int currentField = 0;
        boolean completed = false;

        while (!completed) {
            ui.clearAndShowFullScreen();
            showUserInfoHeader();
            showUserInfoForm(fields, descriptions, currentField);

            String input = getUserInfoInput();

            switch (input) {
                case "up":
                    currentField = Math.max(0, currentField - 1);
                    break;
                case "down":
                    currentField = Math.min(fields.length - 1, currentField + 1);
                    break;
                case "enter":
                    if (editField(currentField)) {
                        updateFieldDisplay(fields, currentField);
                    }
                    break;
                case "confirm":
                    if (validateUserInfo()) {
                        completed = true;
                    }
                    break;
                case "quit":
                    if (ui.confirmAction("Are you sure you want to cancel the installation?")) {
                        System.exit(0);
                    }
                    break;
            }
        }
    }

    private void showUserInfoHeader() {
        System.out.println();
        String header = "System Configuration - Please complete the form.";
        System.out.println(ui.centerText(header, ui.getTerminalWidth()));
        System.out.println();
    }

    private void showUserInfoForm(String[] fields, String[] descriptions, int currentField) {
        int terminalWidth = ui.getTerminalWidth();
        int boxWidth = Math.min(terminalWidth - 10, 70);

        String border = createBorder(boxWidth, "#");
        String middle = "|" + " ".repeat(Math.max(0, boxWidth - 2)) + "|";

        System.out.println(ui.centerText(border, terminalWidth));
        System.out.println(ui.centerText(middle, terminalWidth));

        for (int i = 0; i < fields.length; i++) {
            String fieldLine = createFieldLine(fields[i], i == currentField, boxWidth);
            System.out.println(ui.centerText(fieldLine, terminalWidth));

            if (i == currentField) {
                String descLine = createDescriptionLine(descriptions[i], boxWidth);
                System.out.println(ui.centerText(descLine, terminalWidth));
                System.out.println(ui.centerText(middle, terminalWidth));
            }
        }

        System.out.println(ui.centerText(middle, terminalWidth));

        String confirmLine = createConfirmLine(currentField == fields.length, boxWidth);
        System.out.println(ui.centerText(confirmLine, terminalWidth));

        System.out.println(ui.centerText(border, terminalWidth));

        System.out.println();
        String help = "UP/DOWN: Navigate  ENTER: Edit  C: Confirm  Q: Quit";
        System.out.println(ui.centerText(help, terminalWidth));
    }

    private String createBorder(int boxWidth, String character) {
        int safeWidth = Math.max(2, boxWidth);
        return character + character.repeat(Math.max(0, safeWidth - 2)) + character;
    }

    private String createFieldLine(String field, boolean isSelected, int boxWidth) {
        String prefix = isSelected ? "| > " : "|   ";
        int availableWidth = Math.max(0, boxWidth - prefix.length() - 1);
        String content = field.length() > availableWidth ? field.substring(0, availableWidth) : field;
        int padding = Math.max(0, availableWidth - content.length());
        return prefix + content + " ".repeat(padding) + "|";
    }

    private String createDescriptionLine(String description, int boxWidth) {
        int availableWidth = Math.max(0, boxWidth - 3);
        String content = description.length() > availableWidth ? description.substring(0, availableWidth) : description;
        int padding = Math.max(0, availableWidth - content.length());
        return "| " + content + " ".repeat(padding) + "|";
    }

    private String createConfirmLine(boolean isSelected, int boxWidth) {
        String prefix = isSelected ? "| > " : "|   ";
        String content = "CONFIRM AND CONTINUE";
        int padding = Math.max(0, boxWidth - prefix.length() - content.length() - 1);
        return prefix + content + " ".repeat(padding) + "|";
    }

    private String getUserInfoInput() {
        try {
            String[] cmd = {"/bin/sh", "-c", "stty raw -echo </dev/tty"};
            Runtime.getRuntime().exec(cmd).waitFor();

            int key = System.in.read();
            cmd = new String[]{"/bin/sh", "-c", "stty cooked echo </dev/tty"};
            Runtime.getRuntime().exec(cmd).waitFor();

            if (key == 27) {

                if (System.in.available() > 0) {

                    int next1 = System.in.read();
                    int next2 = System.in.read();

                    if (next1 == 91) {

                        if (next2 == 65) return "up";
                        if (next2 == 66) return "down";

                    }
                }
                return "enter";

            } else if (key == 10 || key == 13) {
                return "enter";
            } else if (key == 'c' || key == 'C') {
                return "confirm";
            } else if (key == 'q' || key == 'Q') {
                return "quit";
            } else if (key == ' ') {
                return "enter";
            }

            return "enter";

        } catch (Exception e) {
            try {
                String[] cmd = {"/bin/sh", "-c", "stty cooked echo </dev/tty"};
                Runtime.getRuntime().exec(cmd).waitFor();

            } catch (Exception ex) {}

            System.out.print("\nCommand (arrows=nav, enter=select, c=confirm, q=quit): ");

            String input = ui.scanner.nextLine().trim().toLowerCase();

            if (input.equals("u")) return "up";
            if (input.equals("d")) return "down";
            if (input.equals("e") || input.equals("")) return "enter";
            if (input.equals("c")) return "confirm";
            if (input.equals("q")) return "quit";

            return "enter";

        }

    }

    private boolean editField(int fieldIndex) {
        switch (fieldIndex) {
            case 0:
                List<String> languages = Arrays.asList("fr", "us", "en", "de", "es", "it");
                language = ui.selectFromList("Select system language", languages);
                return true;

            case 1:
                List<String> layouts = Arrays.asList("us", "fr", "de", "es", "it", "uk");
                keyboardLayout = ui.selectFromList("Select keyboard layout", layouts);

                try {
                    Runtime.getRuntime().exec("loadkeys " + keyboardLayout).waitFor();
                } catch (Exception ignored) {}
                return true;

            case 2:
                machineName = ui.getInput(
                        "Enter machine name (hostname)",
                        HOSTNAME_PATTERN,
                        "Invalid hostname. Must start with letter/number, contain only letters, numbers, and hyphens, max 63 characters."
                );
                return true;

            case 3:
                username = ui.getInput(
                        "Enter your username",
                        USERNAME_PATTERN,
                        "Invalid username. Must start with lowercase letter or underscore, contain only lowercase letters, numbers, hyphens, and underscores, max 32 characters."
                );
                return true;

            case 4:
                password = ui.getPassword("Enter user password (min 4 characters)");
                return true;

            case 5:
                rootPassword = ui.getPassword("Enter root password (min 4 characters)");
                return true;

            case 6:
                List<String> timezones = Arrays.asList(
                        "Europe/Paris", "America/New_York", "America/Los_Angeles",
                        "Europe/London", "Asia/Tokyo", "Australia/Sydney",
                        "Europe/Berlin", "Europe/Madrid", "Europe/Rome"
                );
                timezone = ui.selectFromList("Select your timezone", timezones);
                return true;

            case 7:
                enableSwap = ui.confirmAction("Enable swap file?");
                if (enableSwap) {
                    List<String> swapSizes = Arrays.asList("1GB", "2GB", "4GB", "8GB");
                    String selectedSwap = ui.selectFromList("Select swap size", swapSizes);
                    swapSize = Integer.parseInt(selectedSwap.replace("GB", ""));
                } else {
                    swapSize = 0;
                }
                return true;

            case 8:
                isWireless = ui.confirmAction("Does the system should use Wireless connection?");
                if (isWireless) {
                    networkName = ui.getInputWithoutConfirmation(
                            "Enter network name (SSID)",
                            "^.{1,32}$",
                            "Network name must be between 1 and 32 characters."
                    );
                    networkPassword = ui.getInputWithoutConfirmation(
                            "Enter network password",
                            "^.{8,64}$",
                            "Network password must be between 8 and 64 characters."
                    );
                }
                return true;

            case 9:
                enableSSH = ui.confirmAction("Enable SSH server?");
                return true;
        }
        return false;
    }

    private void updateFieldDisplay(String[] fields, int fieldIndex) {
        switch (fieldIndex) {
            case 0:
                fields[0] = "Language: " + language;
                break;
            case 1:
                fields[1] = "Keyboard Layout: " + keyboardLayout;
                break;
            case 2:
                fields[2] = "Machine Name: " + machineName;
                break;
            case 3:
                fields[3] = "Username: " + username;
                break;
            case 4:
                fields[4] = "User Password: ********";
                break;
            case 5:
                fields[5] = "Root Password: ********";
                break;
            case 6:
                fields[6] = "Timezone: " + timezone;
                break;
            case 7:
                fields[7] = "Swap File: " + (enableSwap ? swapSize + "GB" : "Disabled");
                break;
            case 8:
                fields[8] = "Wireless: " + (isWireless ? "Enabled (NOT IMPLEMENTED YET)" : "Disabled");
                break;
            case 9:
                fields[9] = "SSH: " + (enableSSH ? "Enabled" : "Disabled");
                break;
        }
    }

    private boolean validateUserInfo() {
        List<String> errors = new ArrayList<>();

        if (language == null || language.isEmpty()) {
            errors.add("Language is required");
        }

        if (keyboardLayout == null || keyboardLayout.isEmpty()) {
            errors.add("Keyboard layout is required");
        }

        if (machineName == null || !machineName.matches(HOSTNAME_PATTERN)) {
            errors.add("Invalid machine name");
        }

        if (username == null || !username.matches(USERNAME_PATTERN)) {
            errors.add("Invalid username");
        }

        if (password == null || password.length() < 4) {
            errors.add("User password must be at least 4 characters");
        }

        if (rootPassword == null || rootPassword.length() < 4) {
            errors.add("Root password must be at least 4 characters");
        }

        if (timezone == null || timezone.isEmpty()) {
            errors.add("Timezone is required");
        }

        if (isWireless && (networkName == null || networkPassword == null)) {
            errors.add("Wireless network requires both SSID and password");
        }

        if (!errors.isEmpty()) {
            ui.clearAndShowFullScreen();

            StringBuilder errorMsg = new StringBuilder("Please fix the following errors:\n\n");
            for (String error : errors) {
                errorMsg.append("- ").append(error).append("\n");
            }
            errorMsg.append("\nPress ENTER to return to the form...");

            System.out.println(ui.centerText("VALIDATION ERRORS", ui.getTerminalWidth()));
            System.out.println();

            String[] errorLines = errorMsg.toString().split("\n");
            for (String line : errorLines) {
                System.out.println(ui.centerText(line, ui.getTerminalWidth()));
            }

            try {
                System.in.read();
            } catch (IOException e) {}

            return false;
        }

        return true;
    }

    private void diskPartition() {
        ui.showSection("DISK PARTITIONING");

        try {
            List<String> drives = getAvailableDrives();
            if (drives.isEmpty()) {
                ui.showError("No storage drives found.");
                System.exit(1);
            }

            String selectedDrive = ui.selectFromList("Select drive to partition:", drives);
            if (selectedDrive == null || selectedDrive.startsWith("<-")) {
                return;
            }

            selectedDrive = selectedDrive.substring(0, selectedDrive.indexOf(" "));

            String[] message1 = {"Press ENTER to launch cfdisk for: " + selectedDrive};
            ui.showContentBoxNoClear(message1);
            scanner.nextLine();

            ProcessBuilder pb = new ProcessBuilder("cfdisk", selectedDrive);
            pb.environment().put("LANG", "en_US.UTF-8");
            pb.environment().put("TERM", "xterm-256color");
            pb.inheritIO();
            Process cfdiskProcess = pb.start();
            int result = cfdiskProcess.waitFor();

            if (result != 0) {
                ui.showError("cfdisk exited with error code: " + result);
                System.exit(1);
                return;
            }

            refreshPartitionTable(selectedDrive);

            List<String> partitions = getPartitionsOnDrive(selectedDrive);
            if (partitions.isEmpty()) {
                ui.showMessage("No partitions found on " + selectedDrive + ". Please create partitions in cfdisk. Please retry");
                diskPartition();
            }

            chosenPartition = ui.selectFromList("Select root partition (for / mount point):", partitions);

            if (isEfiSystem()) {
                List<String> remainingPartitions = new ArrayList<>();
                for (String partition : partitions) {
                    if (!partition.equals(chosenPartition)) {
                        remainingPartitions.add(partition);
                    }
                }

                if (remainingPartitions.isEmpty()) {
                    ui.showError("No other partitions available for EFI system. Please create at least 2 partitions. Please retry.");
                    diskPartition();
                }

                efiPartition = ui.selectFromList("Select partition for EFI system", remainingPartitions);
                try {
                    Process process = new ProcessBuilder("umount", efiPartition.split(" ")[0], "-l", "-f")
                            .inheritIO()
                            .start();

                    process.waitFor();

                } catch (Exception ignored) {}
            }

            try {
                Process process = new ProcessBuilder("umount", chosenPartition.split(" ")[0], "-l", "-f")
                        .inheritIO()
                        .start();

                process.waitFor();
            } catch (Exception ignored) {}

            ui.showMessage("Partition configuration completed.");

        } catch (Exception e) {
            ui.showError("Error during disk partitioning: " + e.getMessage());
        }
    }

    private void refreshPartitionTable(String drive) {
        try {
            Process process = new ProcessBuilder("blockdev", "--rereadpt", drive)
                    .inheritIO()
                    .start();
            process.waitFor();

            Thread.sleep(1000);

        } catch (Exception e) {
            ui.showError("Failed to reread partition table: " + e.getMessage());
        }
    }

    private List<String> getAvailableDrives() {
        List<String> drives = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"lsblk", "-d", "-n", "-o", "NAME,SIZE,TYPE"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3 && "disk".equals(parts[2])) {
                    String driveName = parts[0];
                    if (!driveName.matches("^(ram|loop|fd|sr).*")) {
                        String size = parts[1];
                        drives.add("/dev/" + driveName + " (" + size + ")");
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            ui.showError("Error detecting drives: " + e.getMessage());
        }
        return drives;
    }

    private List<String> getPartitionsOnDrive(String drive) {
        List<String> partitions = new ArrayList<>();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"lsblk", "-ln", "-o", "NAME,SIZE,TYPE", drive});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("part")) {
                    String[] parts = line.trim().split("\\s+", 3);
                    if (parts.length >= 2) {
                        partitions.add("/dev/" + parts[0] + " (" + parts[1] + ")");
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            ui.showError("Error reading partitions: " + e.getMessage());
        }
        return partitions;
    }

    private boolean isEfiSystem() {
        return Files.exists(Paths.get("/sys/firmware/efi"));
    }

    private void installCydra() {
        ui.showSection("INSTALLING CYDRA");

        try {
            formatEXT4(chosenPartition);
            if (isEfiSystem()) {
                formatEFI(efiPartition);
            }

            mountPartition(chosenPartition, "/mnt/install", "ext4");

            if (isEfiSystem()) {
                mountPartition(efiPartition, "/mnt/efi", "vfat");
            }

            extractSystem();
            configureSystem();

            if (enableSwap) {
                createSwapFile();
            }

            configureBootloader();
        } catch (Exception e) {
            ui.showError("Error during Cydra installation: " + e.getMessage());
        }
    }

    private void configureBootloader() throws IOException, InterruptedException {
        Process process;

        Runtime.getRuntime().exec("mount --bind /dev /mnt/install/dev").waitFor();
        Runtime.getRuntime().exec("mount --bind /proc /mnt/install/proc").waitFor();
        Runtime.getRuntime().exec("mount --bind /sys /mnt/install/sys").waitFor();

        if (isEfiSystem()) {
            Runtime.getRuntime().exec("mkdir /mnt/install/efi").waitFor();
            Runtime.getRuntime().exec("mount " + efiPartition.split(" ")[0] + " /mnt/install/efi").waitFor();
            Runtime.getRuntime().exec("mount --bind /sys/firmware/efi/efivars /mnt/install/sys/firmware/efi/efivars");

            process = Runtime.getRuntime().exec(new String[]{
                    "chroot", "/mnt/install",
                    "grub-install",
                    "--target=x86_64-efi",
                    "--efi-directory=/efi",
                    "--bootloader-id=CydraLite",
                    "--recheck",
                    "--no-floppy"
            });

            handleProcessOutput(process);

        } else {
            String part = chosenPartition.split(" ")[0];
            String device;
            if (part.contains("nvme")) {
                device = part.replaceAll("p\\d+$", "");
            } else {
                device = part.replaceAll("\\d+$", "");
            }

            process = Runtime.getRuntime().exec(new String[]{
                    "chroot", "/mnt/install",
                    "grub-install",
                    "--target=i386-pc",
                    "--force",
                    device
            });
            handleProcessOutput(process);
        }


        String errorOutput = new String(process.getErrorStream().readAllBytes());
        String stdOutput = new String(process.getInputStream().readAllBytes());

        if (process.waitFor() != 0) {
            throw new IOException(
                    "Installation of grub failed (exit code " + process.waitFor() + "):\n" +
                            "STDOUT:\n" + stdOutput + "\n" +
                            "STDERR:\n" + errorOutput
            );
        }

        Path grubPath = Paths.get("/mnt/install/boot/grub/grub.cfg");
        Files.createDirectories(grubPath.getParent());

        List<String> grubLines = new ArrayList<>();
        grubLines.add("#Cydralite grub.cfg file.");
        grubLines.add("set default=0");
        grubLines.add("set timeout=5");
        grubLines.add("");
        grubLines.add("insmod part_gpt");
        grubLines.add("insmod ext2");
        grubLines.add("search --no-floppy --fs-uuid --set=root " + getPartitionUuid(chosenPartition.split(" ")[0]));
        grubLines.add("");
        grubLines.add("insmod efi_gop");
        grubLines.add("insmod efi_uga");
        grubLines.add("if loadfont /boot/grub/fonts/unicode.pf2; then");
        grubLines.add("  terminal_output gfxterm");
        grubLines.add("fi");
        grubLines.add("");
        grubLines.add("menuentry \"GNU/Linux, CydraLite 6.13.4\" {");
        grubLines.add("  linux   /boot/vmlinuz-6.13.4-lfs-12.3-systemd root=UUID=" + getPartitionUuid(chosenPartition.split(" ")[0] + " ro debug"));
        grubLines.add("  initrd  /boot/initrd.img-6.13.4");
        grubLines.add("}");
        grubLines.add("");
        grubLines.add("if [ \"$grub_platform\" = \"efi\" ]; then");
        grubLines.add("  menuentry \"Firmware Setup\" {");
        grubLines.add("    fwsetup");
        grubLines.add("  }");
        grubLines.add("fi");

        Files.write(grubPath, grubLines);
    }

    private void handleProcessOutput(Process process) throws IOException, InterruptedException {
        String errorOutput = new String(process.getErrorStream().readAllBytes());
        String stdOutput = new String(process.getInputStream().readAllBytes());

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(
                    "Installation of grub failed (exit code " + exitCode + "):\n" +
                            "STDOUT:\n" + stdOutput + "\n" +
                            "STDERR:\n" + errorOutput
            );
        }
    }

    private void formatEXT4(String partition) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"mkfs.ext4", partition.split(" ")[0]});
        if (process.waitFor() != 0) {

            InputStream errorStream = process.getErrorStream();
            String errorMessage = new String(errorStream.readAllBytes());
            throw new IOException("Formatting partition failed: " + errorMessage);
        }
    }

    private void formatEFI(String partition) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"mkfs.vfat", "-F", "32", partition.split(" ")[0]});
        if (process.waitFor() != 0) {

            InputStream errorStream = process.getErrorStream();
            String errorMessage = new String(errorStream.readAllBytes());
            throw new IOException("Formatting partition failed: " + errorMessage);
        }
    }

    private void mountPartition(String partition, String mountPoint, String fsType) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"mount", "-t", fsType, partition.split(" ")[0], mountPoint});
        if (process.waitFor() != 0) {
            InputStream errorStream = process.getErrorStream();
            String errorMessage = new String(errorStream.readAllBytes());
            throw new IOException("Formatting partition failed: " + errorMessage);
        }
    }

    private void extractSystem() throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{
                "/usr/local/bin/unsquashfs", "-f", "-d", "/mnt/install",  "/root/filesystem.squashfs"
        });

        if (process.waitFor() != 0) {
            InputStream errorStream = process.getErrorStream();
            String errorMessage = new String(errorStream.readAllBytes());
            throw new IOException("Formatting partition failed: " + errorMessage);
        }
    }

    private void configureSystem() throws IOException {
        String chosenPartitionUuid = getPartitionUuid(chosenPartition);
        String efiPartitionUuid = null;
        if (isEfiSystem()) efiPartitionUuid = getPartitionUuid(efiPartition);


        Path fstabPath = Paths.get("/mnt/install/etc/fstab");
        Files.createDirectories(fstabPath.getParent());

        List<String> fstabLines = new ArrayList<>();
        fstabLines.add("#CydraLite fstab file. Make a backup before altering its content.");
        fstabLines.add("");
        fstabLines.add("UUID=" + chosenPartitionUuid + "      /            ext4    defaults                                1     1");
        if (enableSwap) fstabLines.add("/swapfile                         swap         swap    pri=1                                   0     0");
        if (isEfiSystem()) fstabLines.add("UUID=" + efiPartitionUuid + "     /boot/efi    vfat    codepage=437,iocharset=iso8859-1        0     1");

        Files.write(fstabPath, fstabLines);

        Files.write(Paths.get("/mnt/install/etc/hostname"), Collections.singletonList(machineName));

        Path localtimePath = Paths.get("/mnt/install/etc/localtime");
        if (!Files.exists(localtimePath)) {
            Files.createSymbolicLink(localtimePath, Paths.get("/usr/share/zoneinfo/" + timezone));
        }

        Path localeConfPath = Paths.get("/mnt/install/etc/locale.conf");
        String locale = "LANG=" + getLocaleForLanguage(language);
        Files.write(localeConfPath, Collections.singletonList(locale));

        Path vconsolePath = Paths.get("/mnt/install/etc/vconsole.conf");
        Files.write(vconsolePath, Collections.singletonList("KEYMAP=" + keyboardLayout));

        if (isWireless) {
            //configureWirelessNetwork();
        }

        Path issuePath = Paths.get("/mnt/install/etc/issue");
        if (Files.exists(issuePath)) {
            List<String> issueLines = Files.readAllLines(issuePath);
            if (issueLines.size() >= 6) {
                issueLines.subList(3, 6).clear();
            } else if (issueLines.size() > 3) {
                issueLines.subList(3, issueLines.size()).clear();
            }
            Files.write(issuePath, issueLines);
        }
    }

    private String getLocaleForLanguage(String lang) {
        switch (lang) {
            case "fr": return "fr_FR.UTF-8";
            case "de": return "de_DE.UTF-8";
            case "es": return "es_ES.UTF-8";
            case "it": return "it_IT.UTF-8";
            default: return "en_US.UTF-8";
        }
    }

    private String getPartitionUuid(String partition) throws IOException {
        Process process = Runtime.getRuntime().exec(new String[]{"blkid", "-s", "UUID", "-o", "value", partition.split(" ")[0]});
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String uuid = reader.readLine();
        if (uuid == null) {
            throw new IOException("Could not get UUID for partition: " + partition.split(" ")[0]);
        }
        return uuid;
    }

    private void configureWirelessNetwork() throws IOException {
        Path wpaSupplicantPath = Paths.get("/mnt/install/etc/wpa_supplicant/wpa_supplicant.conf");
        Files.createDirectories(wpaSupplicantPath.getParent());

        List<String> wpaConfig = Arrays.asList(
                "ctrl_interface=/var/run/wpa_supplicant",
                "update_config=1",
                "",
                "network={",
                "    ssid=\"" + networkName + "\"",
                "    psk=\"" + networkPassword + "\"",
                "}"
        );

        Files.write(wpaSupplicantPath, wpaConfig);
    }

    private void createSwapFile() throws IOException, InterruptedException {
        Path swapfilePath = Paths.get("/mnt/install/swapfile");

        Process ddProcess = Runtime.getRuntime().exec(new String[]{
                "dd", "if=/dev/zero", "of=" + swapfilePath.toString(), "bs=1M", "count=" + (swapSize * 1024)
        });

        if (ddProcess.waitFor() != 0) {
            throw new IOException("Swap file creation failed");
        }

        Process chmodProcess = Runtime.getRuntime().exec(new String[]{"chmod", "600", swapfilePath.toString()});
        if (chmodProcess.waitFor() != 0) {
            throw new IOException("Failed to set swap file permissions");
        }

        Process mkswapProcess = Runtime.getRuntime().exec(new String[]{"mkswap", swapfilePath.toString()});
        if (mkswapProcess.waitFor() != 0) {
            throw new IOException("Failed to initialize swap space");
        }
    }

    private void createUserAccount() throws IOException, InterruptedException {
        ui.showSection("UPDATING USER ACCOUNT");

        Process renameProcess = Runtime.getRuntime().exec(new String[]{
                "chroot", "/mnt/install", "usermod", "-l", username, "cydra"
        });
        int renameExit = renameProcess.waitFor();
        String renameErrors = new String(renameProcess.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (renameExit != 0) {
            throw new IOException("Failed to rename 'cydra' to '" + username +
                    "'. Exit code: " + renameExit + ". Error output: " + renameErrors);
        }

        Process moveHomeProcess = Runtime.getRuntime().exec(new String[]{
                "chroot", "/mnt/install", "usermod", "-d", "/home/" + username, "-m", username
        });
        int moveHomeExit = moveHomeProcess.waitFor();
        String moveHomeErrors = new String(moveHomeProcess.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (moveHomeExit != 0) {
            throw new IOException("Failed to move home directory for '" + username +
                    "'. Exit code: " + moveHomeExit + ". Error output: " + moveHomeErrors);
        }

        Process passwdProcess = Runtime.getRuntime().exec(new String[]{
                "chroot", "/mnt/install", "chpasswd"
        });
        try (PrintWriter writer = new PrintWriter(passwdProcess.getOutputStream())) {
            writer.println(username + ":" + password);
            writer.println("root:" + rootPassword);
        }

        int passwdExit = passwdProcess.waitFor();
        String passwdErrors = new String(passwdProcess.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (passwdExit != 0) {
            throw new IOException("Failed to set passwords. Exit code: "
                    + passwdExit + ". Error output: " + passwdErrors);
        }
    }

    private void systemConfiguration() throws IOException, InterruptedException {
        ui.showSection("SYSTEM CONFIGURATION");
        if (enableSSH) {
            Process sshProcess = Runtime.getRuntime().exec(new String[]{
                    "chroot", "/mnt/install", "systemctl", "enable", "sshd"
            });
            sshProcess.waitFor();
        }

        Process hostidProcess = Runtime.getRuntime().exec(new String[]{
                "chroot", "/mnt/install", "systemd-machine-id-setup"
        });
        hostidProcess.waitFor();
    }

    private void cleanLive() {
        ui.showSection("CLEANING LIVECD BEFORE REBOOTING");

        try {
            Process umountInstall = Runtime.getRuntime().exec(new String[]{"umount", "/mnt/install"});
            umountInstall.waitFor();

            if (efiPartition != null) {
                Process umountEfi = Runtime.getRuntime().exec(new String[]{"umount", "/mnt/efi"});
                umountEfi.waitFor();
            }
        } catch (Exception e) {
            ui.showError("Error during cleanup: " + e.getMessage());
        }
    }

    private void offerReboot() {
        if (ui.confirmAction("Installation complete! Would you like to reboot now?")) {
            try {
                Process rebootProcess = Runtime.getRuntime().exec(new String[]{"reboot"});
                rebootProcess.waitFor();
            } catch (Exception e) {
                ui.showError("Reboot fail. Proceed manually");
            }
        } else {
            ui.showMessage("Thanks for using the CydraLite installer");
        }
    }

    public static boolean isSystemInstalling() {
        return isInstalling;
    }
}