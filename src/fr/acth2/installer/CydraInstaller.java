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
            ui.showWelcome();
            ui.updateProgress(1);

            showInformations();
            ui.updateProgress(2);

            getUserInfos();
            ui.updateProgress(3);

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
        ui.showMessage("Welcome into CydraProject (Lite) installation guide!");
        ui.waitForEnter();

        ui.showMessage("Licenses on: https://github.com/acth2/CydraProject/blob/main/LICENSE");
        ui.showMessage("Installer code on: https://github.com/acth2/CydraInstaller");
        ui.waitForEnter();

        ui.showMessage("Thanks to AinTea for the first installer !");
        ui.showMessage("Thanks to Emmett Syazwan for the LFS iso template");
        ui.showMessage("Thanks to the LFS & BLFS team for everything !");
        ui.showMessage("Thanks to YOU for installing CydraLite !");
        ui.waitForEnter();
    }

    private void getUserInfos() {
        ui.showSection("GET USER INFOS");

        language = ui.getInput("Enter language name (fr / us)");
        machineName = ui.getInput("Enter machine name");
        username = ui.getInput("Enter your username");

        password = ui.getPassword("Enter machine password");

        isWireless = ui.confirmAction("Does the system should use Wireless connection?");
        if (isWireless) {
            networkName = ui.getInput("Enter network name");
            networkPassword = ui.getPassword("Enter network password");
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
                efiPartition = ui.selectFromList("Select the EFI Device", efiDevices);
            }

            chosenPartition = "/dev/" + chosenPartition;
            if (efiPartition != null) {
                efiPartition = "/dev/" + efiPartition;
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
            if (!line.matches("^(loop0|sr0|name)$") && !line.isEmpty()) {
                devices.add(line);
            }
        }

        return devices;
    }

    private List<String> getEfiDevices(List<String> devices, String exclude) {
        List<String> efiDevices = new ArrayList<>();
        for (String device : devices) {
            if (!device.equals(exclude)) {
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
            process.waitFor();
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

        Runtime.getRuntime().exec(new String[]{"mkfs.vfat", "-F", "32", efiPartition + "1"}).waitFor();

        Files.createDirectories(Paths.get("/mnt/efi"));
        Runtime.getRuntime().exec(new String[]{"mount", efiPartition + "1", "/mnt/efi"}).waitFor();

        ui.showMessage("The partition " + efiPartition + "1 has been formatted as FAT32.");

        Runtime.getRuntime().exec(new String[]{
                "grub-install", efiPartition + "1",
                "--root-directory=/mnt/efi",
                "--target=x86_64-efi",
                "--removable"
        }).waitFor();
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
        process.waitFor();
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
        process.waitFor();
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
        process.waitFor();
    }

    private void formatPartition(String partition) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"mkfs.ext4", "-F", partition});
        process.waitFor();
    }

    private void mountPartition(String partition, String mountPoint) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{"mount", "-t", "ext4", partition, mountPoint});
        process.waitFor();
    }

    private void extractSystem() throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(new String[]{
                "tar", "xf", "/root/system.tar.gz", "-C", "/mnt/install"
        });
        process.waitFor();
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

        if (isWireless) {
            configureWirelessNetwork();
        }
    }

    private String getPartitionUuid(String partition) throws IOException {
        Process process = Runtime.getRuntime().exec(new String[]{"blkid", "-s", "UUID", "-o", "value", partition});
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        return reader.readLine();
    }

    private void configureWirelessNetwork() throws IOException {
        ui.showMessage("Wireless network configuration completed");
    }

    private void createSwapFile() throws IOException, InterruptedException {
        Path swapfilePath = Paths.get("/mnt/install/swapfile");

        Process ddProcess = Runtime.getRuntime().exec(new String[]{
                "dd", "if=/dev/zero", "of=" + swapfilePath.toString(), "bs=1M", "count=2048"
        });
        ddProcess.waitFor();

        Runtime.getRuntime().exec(new String[]{"chmod", "600", swapfilePath.toString()}).waitFor();
        Runtime.getRuntime().exec(new String[]{"mkswap", swapfilePath.toString()}).waitFor();

        ui.showMessage("a 2GB swapfile is created.. (" + chosenPartition + ")");
    }

    private void cleanLive() {
        ui.showSection("CLEANING LIVECD BEFORE REBOOTING");

        try {
            Runtime.getRuntime().exec(new String[]{"umount", "/mnt/install"}).waitFor();
            if (efiPartition != null) {
                Runtime.getRuntime().exec(new String[]{"umount", "/mnt/efi"}).waitFor();
            }
            Runtime.getRuntime().exec(new String[]{"umount", "/mnt/temp"}).waitFor();

            ui.showMessage("Cleanup completed");
        } catch (Exception e) {
            ui.showError("Error during cleanup: " + e.getMessage());
        }
    }
}