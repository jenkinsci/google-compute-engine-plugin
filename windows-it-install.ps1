# Following script works with the Windows 2016 image provided by GCE.
# Script may not work for other images/versions of Powershell.
# We are in the first phase where we need to configure PowerShell, install Chocolately, install the OpenSSH Server and the restart the VM.

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
$username = "Build"
$password = ConvertTo-SecureString "P4ssword1" -AsPlainText -Force
New-LocalUser -Name $username -Password $password
Add-LocalGroupMember -Group "Administrators" -Member "$username"

# Following step needed for user to show up in HKLM.
Write-Output "Simulating login to register user..."
$cred = New-Object System.Management.Automation.PSCredential -ArgumentList $username,$password
Start-Process cmd /c -WindowStyle Hidden -Credential $cred -ErrorAction SilentlyContinue
