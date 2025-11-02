package fr.acth2.installer;

import fr.acth2.installer.ui.InstallerUI;

import java.io.*;
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
    private boolean enableFirewall;
    private boolean enableSSH;
    private String rootPassword;
    public static boolean error = false;

    private static final String LANGUAGE_PATTERN = "^(fr|us|en|de|es|it)$";
    private static final String HOSTNAME_PATTERN = "^[a-zA-Z0-9][a-zA-Z0-9-]{0,62}$";
    private static final String USERNAME_PATTERN = "^[a-z_][a-z0-9_-]{0,31}$";
    private static final String TIMEZONE_PATTERN = "^[A-Za-z]+/[A-Za-z_]+$";
    private static final String KEYBOARD_PATTERN = "^(us|fr|de|es|it|uk)$";

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

            if (ui.confirmAction("From here. Your PC data will be erased. Continue?")) {
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
            diskPartition();
            ui.updateProgress(5);

            installCydra();
            ui.updateProgress(8);

            createUserAccount();
            ui.updateProgress(9);

            systemConfiguration();
            ui.updateProgress(10);

            cleanLive();
            ui.updateProgress(11);

            ui.showMessage("The Installation is finished, thanks for using CydraLite !");
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
                "Firewall: " + (enableFirewall ? "Enabled" : "Disabled"),
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
                enableFirewall = ui.confirmAction("Enable basic firewall?");
                return true;

            case 10:
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
                fields[8] = "Wireless: " + (isWireless ? "Enabled" : "Disabled");
                break;
            case 9:
                fields[9] = "Firewall: " + (enableFirewall ? "Enabled" : "Disabled");
                break;
            case 10:
                fields[10] = "SSH: " + (enableSSH ? "Enabled" : "Disabled");
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

            String[] message1 = {"Opening cfdisk for: " + selectedDrive};
            ui.showContentBoxNoClear(message1);

            String[] message2 = {"Please configure partitions in cfdisk. When finished, save and exit cfdisk to continue."};
            ui.showContentBoxNoClear(message2);

            String[] message3 = {"Press ENTER to launch cfdisk..."};
            ui.showContentBoxNoClear(message3);

            scanner.nextLine();

            ProcessBuilder pb = new ProcessBuilder("cfdisk", selectedDrive);
            pb.inheritIO();
            Process cfdiskProcess = pb.start();
            int result = cfdiskProcess.waitFor();

            if (result != 0) {
                ui.showError("cfdisk exited with error code: " + result);
                System.exit(1);
                return;
            }

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

                efiPartition = ui.selectFromList("Select partition for EFI system (will be formatted as FAT32):", remainingPartitions);
            }

            ui.showMessage("Partition configuration completed.");

        } catch (Exception e) {
            ui.showError("Error during disk partitioning: " + e.getMessage());
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

    private List<String> getEfiPartitions(List<String> partitions) {
        List<String> efiPartitions = new ArrayList<>();
        try {
            for (String partition : partitions) {
                String partDevice = partition.substring(0, partition.indexOf(" "));

                Process typeProcess = Runtime.getRuntime().exec(new String[]{"blkid", "-o", "value", "-s", "PART_ENTRY_TYPE", partDevice});
                BufferedReader typeReader = new BufferedReader(new InputStreamReader(typeProcess.getInputStream()));
                String partType = typeReader.readLine();
                typeProcess.waitFor();

                Process fsProcess = Runtime.getRuntime().exec(new String[]{"blkid", "-o", "value", "-s", "TYPE", partDevice});
                BufferedReader fsReader = new BufferedReader(new InputStreamReader(fsProcess.getInputStream()));
                String fstype = fsReader.readLine();
                fsProcess.waitFor();

                boolean isEfi = false;

                if (partType != null) {
                    if (partType.equalsIgnoreCase("c12a7328-f81f-11d2-ba4b-00a0c93ec93b")) {
                        isEfi = true;
                    }

                    else if (partType.equalsIgnoreCase("ef")) {
                        isEfi = true;
                    }
                }

                if (fstype != null && fstype.equalsIgnoreCase("vfat")) {
                    isEfi = true;
                }

                if (isEfi) {
                    efiPartitions.add(partition);
                }
            }
        } catch (Exception e) {
            ui.showError("Error checking partition types: " + e.getMessage());
        }
        return efiPartitions;
    }

    private boolean isEfiSystem() {
        return Files.exists(Paths.get("/sys/firmware/efi"));
    }

    private void installCydra() {
        ui.showSection("INSTALLING CYDRA");

        try {
            if (isEfiSystem()) {
                formatEFI(efiPartition);
            }

            mountPartition(chosenPartition, "/mnt/install");

            if (isEfiSystem()) {
                mountPartition(efiPartition, "/mnt/efi");
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
        if (isEfiSystem()) {
            Process process = Runtime.getRuntime().exec(new String[]{"grub-install", "--target=x86_64-", "--efi-directory=/mnt/efi", "--bootloader-id=CydraLite", "--recheck", "--no-floppy"});
            if (process.waitFor() != 0)
                throw new IOException("Installation of grub failed: " + process.getErrorStream());
        } else {
            Process process = Runtime.getRuntime().exec(new String[]{"grub-install", "--target=i386-pc", chosenPartition});
            if (process.waitFor() != 0)
                throw new IOException("Installation of grub failed: " + process.getErrorStream());
        }

        Path grubPath = Paths.get("/mnt/install/boot/grub/grub.cfg");
        Files.createDirectories(grubPath.getParent());

        List<String> grubLines = new ArrayList<>();
        grubLines.add("#Cydralite grub.cfg file. Operate with precaution.");
        grubLines.add("set default=0");
        grubLines.add("set timeout=5");
        grubLines.add("");
        grubLines.add("insmod part_gpt");
        grubLines.add("insmod ext2");
        grubLines.add("set root=" + convertToGrubFormat(chosenPartition));
        grubLines.add("");
        grubLines.add("insmod efi_gop");
        grubLines.add("insmod efi_uga");
        grubLines.add("if loadfont /boot/grub/fonts/unicode.pf2; then");
        grubLines.add("  terminal_output gfxterm");
        grubLines.add("fi");
        grubLines.add("");
        grubLines.add("menuentry \"GNU/Linux, CydraLite 6.13.4\" {");
        grubLines.add("  linux   /boot/vmlinuz-6.13.4-lfs-12.3-systemd root=/dev/sda ro debug");
        grubLines.add("  initrd  /boot/initrd.img-6.13.4");
        grubLines.add("}");
        grubLines.add("");
        grubLines.add("menuentry \"Firmware Setup\" {");
        grubLines.add("  fwsetup");
        grubLines.add("}");

        Files.write(grubPath, grubLines);
    }

    public static String convertToGrubFormat(String mainPartition) {
        try {
            String diskLetter = mainPartition.replaceAll(".*/dev/sd([a-z]).*", "$1");
            if (diskLetter.isEmpty()) {
                throw new IllegalArgumentException("Invalid disk format: " + mainPartition);
            }

            String partitionStr = mainPartition.replaceAll(".*/dev/sd[a-z]([0-9]+)?.*", "$1");
            int diskNumber = diskLetter.charAt(0) - 'a';
            int partitionNumber = partitionStr.isEmpty() ? 0 : Integer.parseInt(partitionStr) - 1;

            return String.format("(hd%d,%d)", diskNumber, partitionNumber);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse partition: " + mainPartition, e);
        }
    }

    private void formatEFI(String partition) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"mkfs.vfat", "-F", partition});
        if (process.waitFor() != 0) {
            throw new IOException("Formatting partition failed");
        }
    }

    private void mountPartition(String partition, String mountPoint) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"mount", "-t", "ext4", partition, mountPoint});
        if (process.waitFor() != 0) {
            throw new IOException("Mounting partition failed");
        }
    }

    private void extractSystem() throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{
                "tar", "xf", "/root/system.tar.gz", "-C", "/mnt/install"
        });
        if (process.waitFor() != 0) {
            throw new IOException("System extraction failed");
        }
    }

    private void configureSystem() throws IOException {
        String chosenPartitionUuid = getPartitionUuid(chosenPartition);
        String efiPartitionUuid = getPartitionUuid(efiPartition);

        Path fstabPath = Paths.get("/mnt/install/etc/fstab");
        Files.createDirectories(fstabPath.getParent());

        List<String> fstabLines = new ArrayList<>();
        fstabLines.add("#CydraLite FSTAB File, Make a backup if you want to modify it..");
        fstabLines.add("");
        fstabLines.add("UUID=" + chosenPartitionUuid + "      /            ext4    defaults                                1     1");

        if (enableSwap) {
            fstabLines.add("/swapfile                         swap         swap    pri=1                                   0     0");
        }

        if (isEfiSystem()) {
            fstabLines.add("UUID=" + efiPartitionUuid + "     /boot/efi    vfat    codepage=437,iocharset=iso8859-1        0     1");
        }

        Files.write(fstabPath, fstabLines);

        Files.write(Paths.get("/mnt/install/etc/hostname"),
                Collections.singletonList(machineName));

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
            configureWirelessNetwork();
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
        Process process = Runtime.getRuntime().exec(new String[]{"blkid", "-s", "UUID", "-o", "value", partition});
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String uuid = reader.readLine();
        if (uuid == null) {
            throw new IOException("Could not get UUID for partition: " + partition);
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
        ui.showSection("CREATING USER ACCOUNT");

        Process userAddProcess = Runtime.getRuntime().exec(new String[]{
                "chroot", "/mnt/install", "useradd", "-m", "-G", "wheel", "-s", "/bin/bash", username
        });

        Process addSudoerProcess = Runtime.getRuntime().exec(new String[]{
                "chroot", "/mnt/install", "usermod", "-a", "-G", "sudo", username
        });

        if (userAddProcess.waitFor() != 0) {
            throw new IOException("Failed to create user account");
        }

        if (addSudoerProcess.waitFor() != 0) {
            throw new IOException("Failed to add user to sudoer");
        }

        Process passwdProcess = Runtime.getRuntime().exec(new String[]{
                "chroot", "/mnt/install", "chpasswd"
        });

        try (PrintWriter writer = new PrintWriter(passwdProcess.getOutputStream())) {
            writer.println(username + ":" + password);
            writer.println("root:" + rootPassword);
        }
        if (passwdProcess.waitFor() != 0) {
            throw new IOException("Failed to set passwords");
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

        if (enableFirewall) {
            configureFirewall();
        }

        Process hostidProcess = Runtime.getRuntime().exec(new String[]{
                "chroot", "/mnt/install", "systemd-machine-id-setup"
        });
        hostidProcess.waitFor();
    }

    private void configureFirewall() throws IOException, InterruptedException {
        Path firewallRules = Paths.get("/mnt/install/etc/iptables.rules");
        List<String> rules = Arrays.asList(
                "*filter",
                ":INPUT DROP [0:0]",
                ":FORWARD DROP [0:0]",
                ":OUTPUT ACCEPT [0:0]",
                ":TCP - [0:0]",
                ":UDP - [0:0]",
                "-A INPUT -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT",
                "-A INPUT -i lo -j ACCEPT",
                "-A INPUT -m conntrack --ctstate INVALID -j DROP",
                "-A INPUT -p icmp -j ACCEPT",
                "-A INPUT -p tcp -m tcp --dport 22 -j ACCEPT",
                "-A INPUT -p udp -m udp --dport 5353 -j ACCEPT",
                "-A INPUT -p tcp -m tcp --dport 80 -j ACCEPT",
                "-A INPUT -p tcp -m tcp --dport 443 -j ACCEPT",
                "-A INPUT -j REJECT --reject-with icmp-port-unreachable",
                "COMMIT"
        );
        Files.write(firewallRules, rules);

        Process enableFirewallProcess = Runtime.getRuntime().exec(new String[]{
                "chroot", "/mnt/install", "systemctl", "enable", "iptables"
        });
        enableFirewallProcess.waitFor();
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
}