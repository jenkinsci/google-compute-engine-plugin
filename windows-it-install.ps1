# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
# compliance with the License. You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied. See the License for the specific language governing permissions and limitations under the
# License.
#
# Following script works with the Windows 2016 image provided by GCE.
# Script may not work for other images/versions of Powershell.
# We are in the first phase where we need to configure PowerShell, install Chocolatey, install the OpenSSH Server and the restart the VM.

Write-Output "Setting execution policy for PowerShell scripts...";
Set-ExecutionPolicy Bypass -Scope Process -Force;

Write-Output "Installing Chocolatey...";
Invoke-Expression ((New-Object System.Net.WebClient).DownloadString('https://chocolatey.org/install.ps1'));

Write-Output "Refreshing environment...";
RefreshEnv.cmd

Write-Output "Installing OpenSSH Server..."
choco install -y openssh -params '"/SSHServerFeature /KeyBasedAuthenticationFeature"'
if ($LastExitCode -ne 0) {
exit 1
}

Write-Output "Installing Java 8..."
choco install -y jre8

# Following Step is needed for the startup script in the integration test to work, even if you already configured your own user.
Write-Output "Adding build user..."
$username = "jenkins"
$password = ConvertTo-SecureString $env:WINDOWS_PASSWORD -AsPlainText -Force
New-LocalUser -Name $username -Password $password
Add-LocalGroupMember -Group "Administrators" -Member "$username"

# Following step needed for user to show up in HKLM.
Write-Output "Simulating login to register user..."
$cred = New-Object System.Management.Automation.PSCredential -ArgumentList $username,$password
Start-Process cmd /c -WindowStyle Hidden -Credential $cred -ErrorAction SilentlyContinue

# Close the door on the way out
choco install undo-winrmconfig-during-shutdown --confirm
