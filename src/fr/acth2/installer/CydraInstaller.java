package fr.acth2.installer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.security.SecureRandom;

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
            ui.updateProgress(1);

            diskPartition();
            ui.updateProgress(4);

            if (ui.confirmAction("The Installation will start. Continue?")) {
                if (validateInputs()) {
                    if (ui.confirmAction("!! WARNING !!\n\nEVERY DATA ON THE DISK WILL BE ERASED.\nDo you want to continue?")) {
                        diskInstall();
                        ui.updateProgress(5);

                        grubConfigure();
                        ui.updateProgress(6);

                        installCydra();
                        ui.updateProgress(7);

                        createUserAccount();
                        ui.updateProgress(8);

                        systemConfiguration();
                        ui.updateProgress(9);

                        cleanLive();
                        ui.updateProgress(10);

                        ui.showMessage("The Installation is finished, thanks for using CydraLite !");
                        offerReboot();
                    }
                }
            }
        } catch (Exception e) {
            ui.showError("Installation failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    private void checkRootPrivileges() {
        if (!System.getProperty("user.name").equals("root")) {
            ui.showError("This installer must be run as root!");
            System.exit(1);
        }
    }

    private boolean validateInputs() {
        if (password == null || username == null || machineName == null || chosenPartition == null) {
            ui.showError("Missing required information. Installation failed.");
            return false;
        } else if (isWireless && (networkName == null || networkPassword == null)) {
            ui.showError("Missing network information. Installation failed.");
            return false;
        }
        return true;
    }

    private void showInformations() {
        ui.showSection("INFORMATIONS");

        ui.showMessage("Licenses on: https://github.com/acth2/CydraProject/blob/main/LICENSE");
        ui.showMessage("Installer code on: https://github.com/acth2/CydraInstaller");
        ui.showMessage("Thanks to the LFS & BLFS team for everything !");

        if (!ui.confirmAction("This installer will set up CydraLite on your system.\nContinue?")) {
            System.exit(0);
        }
    }

    private void getUserInfos() {
        ui.showSection("GET USER INFOS");

        language = ui.getInput(
                "Enter language (fr / us / en / de / es / it)",
                LANGUAGE_PATTERN,
                "Invalid language. Please enter 'fr', 'us', 'en', 'de', 'es', or 'it'."
        );

        keyboardLayout = ui.getInput(
                "Enter keyboard layout (us, fr, de, es, it, uk)",
                KEYBOARD_PATTERN,
                "Invalid keyboard layout."
        );

        machineName = ui.getInput(
                "Enter machine name (hostname)",
                HOSTNAME_PATTERN,
                "Invalid hostname. Must start with letter/number, contain only letters, numbers, and hyphens, max 63 characters."
        );

        username = ui.getInput(
                "Enter your username",
                USERNAME_PATTERN,
                "Invalid username. Must start with lowercase letter or underscore, contain only lowercase letters, numbers, hyphens, and underscores, max 32 characters."
        );

        password = ui.getPassword("Enter user password (min 4 characters)");
        rootPassword = ui.getPassword("Enter root password (min 4 characters)");

        List<String> timezones = Arrays.asList(
                "Europe/Paris", "America/New_York", "America/Los_Angeles",
                "Europe/London", "Asia/Tokyo", "Australia/Sydney",
                "Europe/Berlin", "Europe/Madrid", "Europe/Rome"
        );
        timezone = ui.selectFromList("Select your timezone", timezones);

        enableSwap = ui.confirmAction("Enable swap file?");
        if (enableSwap) {
            List<String> swapSizes = Arrays.asList("1GB", "2GB", "4GB", "8GB");
            String selectedSwap = ui.selectFromList("Select swap size", swapSizes);
            swapSize = Integer.parseInt(selectedSwap.replace("GB", ""));
        }

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

        enableFirewall = ui.confirmAction("Enable basic firewall?");
        enableSSH = ui.confirmAction("Enable SSH server?");
    }

    private void diskPartition() {
        ui.showSection("DISK PARTITIONING");

        try {
            List<String> devices = getDevices();
            if (devices.isEmpty()) {
                ui.showError("No storage devices found.");
                System.exit(1);
            }

            List<String> menuOptions = Arrays.asList(
                    "Auto-partition entire disk",
                    "Manual partitioning",
                    "Use existing partitions"
            );

            String choice = ui.selectFromList("Select partitioning method", menuOptions);

            switch (choice) {
                case "Auto-partition entire disk":
                    autoPartitioning(devices);
                    break;
                case "Manual partitioning":
                    manualPartitioning(devices);
                    break;
                case "Use existing partitions":
                    useExistingPartitions(devices);
                    break;
            }

        } catch (Exception e) {
            ui.showError("Error during disk partitioning: " + e.getMessage());
        }
    }

    private void autoPartitioning(List<String> devices) throws Exception {
        String selectedDisk = ui.selectFromList("Select disk for automatic partitioning", devices);
        selectedDisk = "/dev/" + selectedDisk;

        List<String> schemeOptions = Arrays.asList(
                "Standard (separate root and home)",
                "Simple (single root partition)",
                "LVM partitioning"
        );

        String scheme = ui.selectFromList("Select partitioning scheme", schemeOptions);

        if (isEfiSystem()) {
            configureAutoEfi(selectedDisk, scheme);
        } else {
            configureAutoBios(selectedDisk, scheme);
        }
    }

    private void configureAutoEfi(String disk, String scheme) throws Exception {
        ui.showMessage("Configuring EFI system with " + scheme + " scheme...");

        Process fdiskProcess = Runtime.getRuntime().exec(new String[]{"fdisk", disk});
        try (PrintWriter writer = new PrintWriter(fdiskProcess.getOutputStream())) {
            writer.println("g"); // GPT partition table
            writer.println("n"); // EFI partition
            writer.println("1");
            writer.println();
            writer.println("+512M");
            writer.println("t");
            writer.println("1");

            if (scheme.equals("Standard (separate root and home)")) {
                writer.println("n"); // Root partition
                writer.println("2");
                writer.println();
                writer.println("+20G");
                writer.println("n"); // Home partition
                writer.println("3");
                writer.println();
                writer.println();
            } else if (scheme.equals("LVM partitioning")) {
                writer.println("n"); // LVM partition
                writer.println("2");
                writer.println();
                writer.println();
                writer.println("t");
                writer.println("2");
                writer.println("30");
            } else {
                writer.println("n"); // Root partition
                writer.println("2");
                writer.println();
                writer.println();
            }
            writer.println("w");
        }
        fdiskProcess.waitFor();

        Process efiFormat = Runtime.getRuntime().exec(new String[]{"mkfs.fat", "-F", "32", disk + "1"});
        efiFormat.waitFor();

        if (scheme.equals("Standard (separate root and home)")) {
            Process rootFormat = Runtime.getRuntime().exec(new String[]{"mkfs.ext4", "-F", disk + "2"});
            rootFormat.waitFor();
            Process homeFormat = Runtime.getRuntime().exec(new String[]{"mkfs.ext4", "-F", disk + "3"});
            homeFormat.waitFor();

            chosenPartition = disk + "2";
            efiPartition = disk + "1";
        } else {
            Process rootFormat = Runtime.getRuntime().exec(new String[]{"mkfs.ext4", "-F", disk + "2"});
            rootFormat.waitFor();
            chosenPartition = disk + "2";
            efiPartition = disk + "1";
        }

        showPartitionSummary();
    }

    private void configureAutoBios(String disk, String scheme) throws Exception {
        ui.showMessage("Configuring BIOS system with " + scheme + " scheme...");

        Process fdiskProcess = Runtime.getRuntime().exec(new String[]{"fdisk", disk});
        try (PrintWriter writer = new PrintWriter(fdiskProcess.getOutputStream())) {
            writer.println("o"); // MBR partition table

            if (scheme.equals("Standard (separate root and home)")) {
                writer.println("n"); // Root partition
                writer.println("p");
                writer.println("1");
                writer.println();
                writer.println("+20G");
                writer.println("n"); // Home partition
                writer.println("p");
                writer.println("2");
                writer.println();
                writer.println();
            } else {
                writer.println("n"); // Root partition
                writer.println("p");
                writer.println("1");
                writer.println();
                writer.println();
            }
            writer.println("a"); // Bootable
            writer.println("1");
            writer.println("w");
        }
        fdiskProcess.waitFor();

        if (scheme.equals("Standard (separate root and home)")) {
            Process rootFormat = Runtime.getRuntime().exec(new String[]{"mkfs.ext4", "-F", disk + "1"});
            rootFormat.waitFor();
            Process homeFormat = Runtime.getRuntime().exec(new String[]{"mkfs.ext4", "-F", disk + "2"});
            homeFormat.waitFor();
            chosenPartition = disk + "1";
        } else {
            Process rootFormat = Runtime.getRuntime().exec(new String[]{"mkfs.ext4", "-F", disk + "1"});
            rootFormat.waitFor();
            chosenPartition = disk + "1";
        }

        showPartitionSummary();
    }

    private void manualPartitioning(List<String> devices) throws Exception {
        String selectedDisk = ui.selectFromList("Select disk for manual partitioning", devices);
        selectedDisk = "/dev/" + selectedDisk;

        ui.showMessage("Starting manual partitioning for " + selectedDisk + "\n" +
                "You will be placed in fdisk. Please create your partitions manually.\n" +
                "After finishing, return to continue installation.");

        Process fdiskProcess = Runtime.getRuntime().exec(new String[]{"fdisk", selectedDisk});
        fdiskProcess.waitFor();

        List<String> partitions = getPartitions(selectedDisk);
        if (partitions.isEmpty()) {
            ui.showError("No partitions created. Please try again.");
            manualPartitioning(devices);
            return;
        }

        chosenPartition = ui.selectFromList("Select root partition", partitions);

        if (isEfiSystem()) {
            List<String> efiCandidates = getEfiPartitions(partitions);
            if (!efiCandidates.isEmpty()) {
                efiPartition = ui.selectFromList("Select EFI system partition", efiCandidates);
            }
        }

        formatSelectedPartitions();
        showPartitionSummary();
    }

    private void useExistingPartitions(List<String> devices) throws Exception {
        List<String> allPartitions = new ArrayList<>();
        for (String device : devices) {
            allPartitions.addAll(getPartitions("/dev/" + device));
        }

        if (allPartitions.isEmpty()) {
            ui.showError("No existing partitions found.");
            diskPartition();
            return;
        }

        chosenPartition = ui.selectFromList("Select root partition", allPartitions);

        if (isEfiSystem()) {
            List<String> efiCandidates = getEfiPartitions(allPartitions);
            if (!efiCandidates.isEmpty()) {
                efiPartition = ui.selectFromList("Select EFI system partition", efiCandidates);
            }
        }

        if (ui.confirmAction("Format selected partitions?")) {
            formatSelectedPartitions();
        }

        showPartitionSummary();
    }

    private List<String> getPartitions(String disk) throws Exception {
        List<String> partitions = new ArrayList<>();
        Process process = Runtime.getRuntime().exec(new String[]{"lsblk", "-ln", "-o", "NAME", disk});
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.matches(".*[0-9]$")) {
                partitions.add("/dev/" + line.trim());
            }
        }
        return partitions;
    }

    private List<String> getEfiPartitions(List<String> partitions) throws Exception {
        List<String> efiPartitions = new ArrayList<>();
        for (String partition : partitions) {
            Process process = Runtime.getRuntime().exec(new String[]{"blkid", "-o", "value", "-s", "TYPE", partition});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String fstype = reader.readLine();
            if ("vfat".equals(fstype)) {
                efiPartitions.add(partition);
            }
        }
        return efiPartitions;
    }

    private void formatSelectedPartitions() throws Exception {
        if (ui.confirmAction("Format root partition " + chosenPartition + " as ext4?")) {
            Process format = Runtime.getRuntime().exec(new String[]{"mkfs.ext4", "-F", chosenPartition});
            format.waitFor();
        }

        if (efiPartition != null && ui.confirmAction("Format EFI partition " + efiPartition + " as FAT32?")) {
            Process format = Runtime.getRuntime().exec(new String[]{"mkfs.fat", "-F", "32", efiPartition});
            format.waitFor();
        }
    }

    private void showPartitionSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Partitioning Summary:\n\n");
        summary.append("Root partition: ").append(chosenPartition).append("\n");
        if (efiPartition != null) {
            summary.append("EFI partition: ").append(efiPartition).append("\n");
        }
        summary.append("\nContinue with installation?");

        if (!ui.confirmAction(summary.toString())) {
            diskPartition();
        }
    }

    private List<String> getDevices() throws IOException {
        List<String> devices = new ArrayList<>();
        Process process = Runtime.getRuntime().exec("lsblk -ndo NAME -e 7,11");
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.matches("^(loop[0-9]+|sr[0-9]+)$")) {
                devices.add(line);
            }
        }

        Collections.sort(devices);
        return devices;
    }

    private List<String> getEfiDevices(List<String> devices, String exclude) {
        List<String> efiDevices = new ArrayList<>();
        for (String device : devices) {
            if (!device.equals(exclude) && !device.matches(".*[0-9]$")) {
                efiDevices.add(device);
            }
        }
        return efiDevices;
    }

    private boolean isEfiSystem() {
        return Files.exists(Paths.get("/sys/firmware/efi"));
    }

    private void diskInstall() {
        ui.showSection("INSTALL DISK");
        try {
            Files.createDirectories(Paths.get("/mnt/install"));
            Files.createDirectories(Paths.get("/mnt/efi"));
            Files.createDirectories(Paths.get("/mnt/temp"));

            Process process = Runtime.getRuntime().exec(new String[]{"mkfs.ext4", "-F", chosenPartition});
            int result = process.waitFor();
            if (result != 0) {
                throw new IOException("mkfs.ext4 failed with exit code: " + result);
            }
            ui.showMessage("The partition " + chosenPartition + " has been set to ext4 Partition.");

        } catch (Exception e) {
            ui.showError("Error during disk installation: " + e.getMessage());
        }
    }

    private void grubConfigure() {
        ui.showSection("GRUB CONFIGURING");
        try {
            if (!isEfiSystem()) {
                ui.showMessage("GRUB will be installed on " + chosenPartition + "/boot for BIOS boot.");
                installBiosGrub();
            } else {
                configureEfiSystem();
            }
        } catch (Exception e) {
            ui.showError("Error during GRUB configuration: " + e.getMessage());
        }
    }

    private void installBiosGrub() throws IOException, InterruptedException {
        Process grubProcess = Runtime.getRuntime().exec(new String[]{
                "grub-install", chosenPartition,
                "--root-directory=/mnt/install"
        });
        if (grubProcess.waitFor() != 0) {
            throw new IOException("GRUB installation failed");
        }
    }

    private void configureEfiSystem() throws IOException, InterruptedException {
        if (efiPartition.matches(".*[0-9]$")) {
            createEfiPartition(efiPartition);
        } else {
            createNewEfiPartition(efiPartition);
        }

        Process fatProcess = Runtime.getRuntime().exec(new String[]{"mkfs.vfat", "-F", "32", efiPartition + "1"});
        if (fatProcess.waitFor() != 0) {
            throw new IOException("Failed to format EFI partition as FAT32");
        }

        Files.createDirectories(Paths.get("/mnt/efi"));
        Process mountProcess = Runtime.getRuntime().exec(new String[]{"mount", efiPartition + "1", "/mnt/efi"});
        if (mountProcess.waitFor() != 0) {
            throw new IOException("Failed to mount EFI partition");
        }

        ui.showMessage("The partition " + efiPartition + "1 has been formatted as FAT32.");

        Process grubProcess = Runtime.getRuntime().exec(new String[]{
                "grub-install", efiPartition + "1",
                "--root-directory=/mnt/efi",
                "--target=x86_64-efi",
                "--removable"
        });
        if (grubProcess.waitFor() != 0) {
            throw new IOException("GRUB installation failed");
        }
    }

    private void createEfiPartition(String partition) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"fdisk", partition});
        try (PrintWriter writer = new PrintWriter(process.getOutputStream())) {
            writer.println("d");
            writer.println("n");
            writer.println("p");
            writer.println("1");
            writer.println();
            writer.println();
            writer.println("t");
            writer.println("ef");
            writer.println("w");
        }
        if (process.waitFor() != 0) {
            throw new IOException("fdisk operation failed");
        }
    }

    private void createNewEfiPartition(String device) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"fdisk", device});
        try (PrintWriter writer = new PrintWriter(process.getOutputStream())) {
            writer.println("n");
            writer.println("p");
            writer.println("1");
            writer.println();
            writer.println();
            writer.println("t");
            writer.println("ef");
            writer.println("w");
        }
        if (process.waitFor() != 0) {
            throw new IOException("fdisk operation failed");
        }
    }

    private void installCydra() {
        ui.showSection("INSTALLING CYDRA");

        try {
            if (!chosenPartition.matches(".*[0-9]")) {
                createPartition(chosenPartition);
                formatPartition(chosenPartition + "1");
            } else {
                formatPartition(chosenPartition);
            }

            String mountPartition = chosenPartition.matches(".*[0-9]") ? chosenPartition : chosenPartition + "1";
            mountPartition(mountPartition, "/mnt/install");

            extractSystem();
            configureSystem();

            if (enableSwap) {
                createSwapFile();
            }

            ui.showMessage("Installation completed successfully");

        } catch (Exception e) {
            ui.showError("Error during Cydra installation: " + e.getMessage());
        }
    }

    private void createPartition(String device) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"fdisk", device});
        try (PrintWriter writer = new PrintWriter(process.getOutputStream())) {
            writer.println("n");
            writer.println("p");
            writer.println("1");
            writer.println();
            writer.println();
            writer.println("w");
        }
        if (process.waitFor() != 0) {
            throw new IOException("Partition creation failed");
        }
    }

    private void formatPartition(String partition) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"mkfs.ext4", "-F", partition});
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

        Path fstabPath = Paths.get("/mnt/install/etc/fstab");
        Files.createDirectories(fstabPath.getParent());

        List<String> fstabLines = new ArrayList<>();
        fstabLines.add("#CydraLite FSTAB File, Make a backup if you want to modify it..");
        fstabLines.add("");
        fstabLines.add("UUID=" + chosenPartitionUuid + "     /            ext4    defaults            1     1");

        if (enableSwap) {
            fstabLines.add("/swapfile                         swap         swap    pri=1               0     0");
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
        ui.showMessage("Wireless network configuration completed");
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

        ui.showMessage(swapSize + "GB swapfile is created.. (" + chosenPartition + ")");
    }

    private void createUserAccount() throws IOException, InterruptedException {
        ui.showSection("CREATING USER ACCOUNT");

        Process userAddProcess = Runtime.getRuntime().exec(new String[]{
                "chroot", "/mnt/install", "useradd", "-m", "-G", "wheel", "-s", "/bin/bash", username
        });
        if (userAddProcess.waitFor() != 0) {
            throw new IOException("Failed to create user account");
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

        ui.showMessage("User account created successfully");
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

        ui.showMessage("System configuration completed");
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

            Process umountTemp = Runtime.getRuntime().exec(new String[]{"umount", "/mnt/temp"});
            umountTemp.waitFor();

            ui.showMessage("Cleanup completed");
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
                ui.showMessage("Please reboot manually to start using CydraLite.");
            }
        } else {
            ui.showMessage("Please remember to reboot to start using CydraLite.");
        }
    }
}