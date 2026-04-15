$ErrorActionPreference = "Stop"

Write-Output "AGENT_IMAGE value is: $env:AGENT_IMAGE"
if (-not $env:AGENT_IMAGE) {
    Write-Error "AGENT_IMAGE variable is not set"
    exit 1
}

# --- Install Java 25 (Temurin JRE) ---
# Chocolatey only publishes LTS releases (8, 11, 17, 21). Java 25 is a non-LTS
# release, so we install directly from the Adoptium MSI instead.
Write-Output "Installing Temurin 25 JRE from Adoptium MSI..."
$jreMsi = "$env:TEMP\temurin-25-jre.msi"
$jreUrl = "https://api.adoptium.net/v3/installer/latest/25/ga/windows/x64/jre/hotspot/normal/eclipse"
Invoke-WebRequest -Uri $jreUrl -OutFile $jreMsi -UseBasicParsing
$proc = Start-Process msiexec.exe -ArgumentList "/i `"$jreMsi`" ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome /quiet /norestart" -Wait -NoNewWindow -PassThru
if ($proc.ExitCode -ne 0) {
    Write-Error "MSI install failed with exit code $($proc.ExitCode)"
    exit 1
}
Remove-Item $jreMsi -Force

# Refresh PATH — the MSI adds Java to the system PATH via FeatureEnvironment
$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

# Verify java is on PATH, fallback to searching Program Files if not
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    $javaExe = Get-ChildItem "C:\Program Files" -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($javaExe) {
        $binPath = $javaExe.DirectoryName
        $javaHome = Split-Path $binPath
        [System.Environment]::SetEnvironmentVariable("Path", $env:Path + ";$binPath", "Machine")
        [System.Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "Machine")
        $env:Path += ";$binPath"
        Write-Output "Added $binPath to PATH"
    } else {
        Write-Error "Could not find java.exe under C:\Program Files"
        exit 1
    }
}

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

# Simulate login to register user profile (needed for SSH to work)
$cred = New-Object System.Management.Automation.PSCredential -ArgumentList "jenkins", $password
Start-Process cmd /c -WindowStyle Hidden -Credential $cred -ErrorAction SilentlyContinue
Start-Sleep -Seconds 5

Write-Output "Image provisioning complete."
