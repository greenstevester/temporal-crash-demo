# Install Temporal on Windows

These manual steps install Temporal 1.10.0, the version used for the crash-and-restart demo.
No PowerShell is required.

1. Download the Windows ZIP file for your computer:
   - [Intel or AMD Windows computers](https://github.com/temporalio/cli/releases/download/v1.10.0/temporal_cli_1.10.0_windows_amd64.zip)
   - [Windows computers with an ARM processor](https://github.com/temporalio/cli/releases/download/v1.10.0/temporal_cli_1.10.0_windows_arm64.zip)

2. Open your **Downloads** folder. Right-click the downloaded ZIP file and choose
   **Extract All**, then **Extract**.

3. Open **File Explorer** and create the folder `C:\Tools\Temporal`.
   Copy `temporal.exe` from the extracted files into that folder. Its full location should be
   `C:\Tools\Temporal\temporal.exe`.

4. Open **Start**, search for **environment variables**, and select
   **Edit the system environment variables**.

5. Click **Environment Variables**. Under **User variables**, select **Path** and click **Edit**.

6. Click **New**, enter `C:\Tools\Temporal`, and click **OK** on each open settings dialog.
   Keep the existing entries. This lets Windows find Temporal when you run it from another folder.

7. Close and reopen any terminal or Java development application that was already running.
   They need to restart to pick up the changed Path.

The crash-and-restart demo starts its own Temporal server during the test.

## Sources

- [Temporal installation guide](https://docs.temporal.io/cli/setup-cli)
- [Temporal 1.10.0 release](https://github.com/temporalio/cli/releases/tag/v1.10.0)

