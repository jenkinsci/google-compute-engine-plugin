# Following script works with the Windows 2016 image provided by GCE. If running a different version of Windows/Powershell, changes may be needed
# We are in the first phase where we need to configure PowerShell, install Chocolately, install the OpenSSH Server.
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

# No need to add a user if you've already configured one
Write-Output "Adding build user..."
$username = "Build"
$password = ConvertTo-SecureString "P4ssword1" -AsPlainText -Force

New-LocalUser -Name $username -Password $password
Add-LocalGroupMember -Group "Administrators" -Member "$username"

# Following steps are only needed if you would like to use key-based authentication for SSH
# Following step is needed so that new user will show up in HKLM
Write-Output "Simulating new user login..."
$cred = New-Object System.Management.Automation.PSCredential -ArgumentList $username,$password
Start-Process cmd /c -WindowStyle Hidden -Credential $cred -ErrorAction SilentlyContinue

# You will need to insert your own public key here
Write-Output "Creating key file and writing public key to file"
$ConfiguredPublicKey = "<YOUR PUBLIC KEY HERE. WILL START WITH ssh-rsa>"

# We are in the second phase of startup where we need to set up authorized_keys for the specified user.
$UserSid = Get-WmiObject win32_useraccount -Filter "name = '$username'" | select-object sid -ExpandProperty SID
$UserProfilePath = Get-ItemProperty -Path  "HKLM:\Software\Microsoft\Windows NT\CurrentVersion\ProfileList\$UserSid" -Name ProfileImagePath | select-object profileimagepath -ExpandProperty ProfileImagePath

# Create the .ssh folder and authorized_keys file.
mkdir $UserProfilePath\.ssh
Set-Content -Path $UserProfilePath\.ssh\authorized_keys -Value $ConfiguredPublicKey

# Fix up permissions on authorized_keys
Import-Module "$env:PROGRAMFILES\OpenSSH-Win64\OpenSSHUtils.psd1" -Force
Repair-AuthorizedKeyPermission -FilePath  $UserProfilePath\.ssh\authorized_keys
