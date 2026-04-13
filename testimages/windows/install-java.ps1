$ErrorActionPreference = "Stop"

Write-Output "AGENT_IMAGE value is: $env:AGENT_IMAGE"
if (-not $env:AGENT_IMAGE) {
    Write-Error "AGENT_IMAGE variable is not set"
    exit 1
}

# --- Install Chocolatey ---
Write-Output "Installing Chocolatey..."
Set-ExecutionPolicy Bypass -Scope Process -Force
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
Invoke-Expression ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Refresh PATH to pick up choco
$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

# --- Install Java 21 (Temurin JRE) ---
Write-Output "Installing Temurin 21 JRE..."
choco install -y Temurinjre
if ($LASTEXITCODE -ne 0) { exit 1 }

# Refresh PATH to pick up java
$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

Write-Output "Verifying Java installation..."
java -version
if ($LASTEXITCODE -ne 0) {
    Write-Error "Java installation verification failed"
    exit 1
}

# --- Handle non-standard-java variant ---
if ($env:AGENT_IMAGE -like "*non-standard-java") {
    Write-Output "Configuring non-standard-java variant..."
    $javaPath = (Get-Command java).Source
    $javaDir = Split-Path $javaPath
    $nonStandardPath = Join-Path $javaDir "non-standard-java.exe"
    Copy-Item $javaPath $nonStandardPath
    Remove-Item $javaPath
    & $nonStandardPath -version
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Non-standard java verification failed"
        exit 1
    }
}

# --- Install and configure OpenSSH Server ---
Write-Output "Installing OpenSSH Server..."
Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
Start-Service sshd
Set-Service -Name sshd -StartupType Automatic

# Set default SSH shell to PowerShell
New-ItemProperty -Path "HKLM:\SOFTWARE\OpenSSH" -Name DefaultShell -Value "C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" -PropertyType String -Force

# Ensure PubkeyAuthentication and PasswordAuthentication are enabled in sshd_config
$sshdConfig = "$env:PROGRAMDATA\ssh\sshd_config"
if (Test-Path $sshdConfig) {
    $content = Get-Content $sshdConfig
    $content = $content -replace '#PubkeyAuthentication yes', 'PubkeyAuthentication yes'
    $content = $content -replace '#PasswordAuthentication yes', 'PasswordAuthentication yes'
    Set-Content $sshdConfig $content
    Restart-Service sshd
}

# --- Create jenkins user ---
Write-Output "Creating jenkins user..."
$password = ConvertTo-SecureString $env:JENKINS_PASSWORD -AsPlainText -Force
New-LocalUser -Name "jenkins" -Password $password -PasswordNeverExpires
Add-LocalGroupMember -Group "Administrators" -Member "jenkins"

# Simulate login to register user profile in HKLM (needed for SSH to work)
Write-Output "Simulating jenkins user login to register profile..."
$cred = New-Object System.Management.Automation.PSCredential -ArgumentList "jenkins", $password
Start-Process cmd /c -WindowStyle Hidden -Credential $cred -ErrorAction SilentlyContinue
Start-Sleep -Seconds 5

Write-Output "Image provisioning complete."
