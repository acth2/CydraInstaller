package fr.acth2.installer;

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
    public static boolean error = false;

    // Validation patterns
    private static final String LANGUAGE_PATTERN = "^(fr|us|en)$";
    private static final String HOSTNAME_PATTERN = "^[a-zA-Z0-9][a-zA-Z0-9-]{0,62}$";
    private static final String USERNAME_PATTERN = "^[a-z_][a-z0-9_-]{0,31}$";
    private static final String TIMEZONE_PATTERN = "^[A-Za-z]+/[A-Za-z_]+$";

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

                        cleanLive();
                        ui.updateProgress(8);

                        ui.showMessage("The Installation is finished, thanks for using CydraLite !");
                    }
                }
            }
        } catch (Exception e) {
            ui.showError("Installation failed: " + e.getMessage());
        } finally {
            scanner.close();
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
    }

    private void getUserInfos() {
        ui.showSection("GET USER INFOS");

        language = ui.getInput(
                "Enter language (fr / us / en)",
                LANGUAGE_PATTERN,
                "Invalid language. Please enter 'fr', 'us', or 'en'."
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

        password = ui.getPassword("Enter machine password (min 4 characters)");

        // Timezone selection
        List<String> timezones = Arrays.asList(
                "Europe/Paris", "America/New_York", "America/Los_Angeles",
                "Europe/London", "Asia/Tokyo", "Australia/Sydney"
        );
        timezone = ui.selectFromList("Select your timezone", timezones);

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
    }

    private void diskPartition() {
        ui.showSection("DISK PARTITION");

        try {
            List<String> devices = getDevices();
            if (devices.isEmpty()) {
                ui.showError("No devices found..");
                System.exit(1);
            }

            chosenPartition = ui.selectFromList("Select The System Device", devices);

            if (isEfiSystem()) {
                List<String> efiDevices = getEfiDevices(devices, chosenPartition);
                if (efiDevices.isEmpty()) {
                    ui.showError("No suitable EFI devices found.");
                    System.exit(1);
                }
                efiPartition = ui.selectFromList("Select the EFI Device", efiDevices);
            }

            chosenPartition = "/dev/" + chosenPartition;
            if (efiPartition != null) {
                efiPartition = "/dev/" + efiPartition;
            }

            // Show confirmation with selected devices
            StringBuilder confirmation = new StringBuilder();
            confirmation.append("Selected devices:\n");
            confirmation.append("System: ").append(chosenPartition).append("\n");
            if (efiPartition != null) {
                confirmation.append("EFI: ").append(efiPartition).append("\n");
            }
            confirmation.append("\nThese devices will be formatted. Continue?");

            if (!ui.confirmAction(confirmation.toString())) {
                diskPartition(); // Restart disk selection
            }

        } catch (Exception e) {
            ui.showError("Error during disk partition: " + e.getMessage());
        }
    }

    private List<String> getDevices() throws IOException {
        List<String> devices = new ArrayList<>();
        Process process = Runtime.getRuntime().exec("awk '{print $4}' /proc/partitions");
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.matches("^(loop[0-9]+|sr[0-9]+|name|ram[0-9]+)$") && !line.isEmpty() && line.matches("^[a-z]+[a-z0-9]*$")) {
                devices.add(line);
            }
        }

        // Sort devices for better readability
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
            } else {
                configureEfiSystem();
            }
        } catch (Exception e) {
            ui.showError("Error during GRUB configuration: " + e.getMessage());
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
            createSwapFile();

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

        List<String> fstabLines = Arrays.asList(
                "#CydraLite FSTAB File, Make a backup if you want to modify it..",
                "",
                "UUID=" + chosenPartitionUuid + "     /            ext4    defaults            1     1",
                "/swapfile                         swap         swap    pri=1               0     0"
        );

        Files.write(fstabPath, fstabLines);

        Files.write(Paths.get("/mnt/install/etc/hostname"),
                Collections.singletonList(machineName));

        // Set timezone
        Path localtimePath = Paths.get("/mnt/install/etc/localtime");
        if (!Files.exists(localtimePath)) {
            Files.createSymbolicLink(localtimePath, Paths.get("/usr/share/zoneinfo/" + timezone));
        }

        // Set language
        Path localeConfPath = Paths.get("/mnt/install/etc/locale.conf");
        String locale = "LANG=" + (language.equals("fr") ? "fr_FR.UTF-8" : "en_US.UTF-8");
        Files.write(localeConfPath, Collections.singletonList(locale));

        if (isWireless) {
            configureWirelessNetwork();
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
        // Create basic wireless configuration
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
                "dd", "if=/dev/zero", "of=" + swapfilePath.toString(), "bs=1M", "count=2048"
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

        ui.showMessage("a 2GB swapfile is created.. (" + chosenPartition + ")");
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
}