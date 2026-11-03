# Runs CatchupLiveCheck against the live feeds using the app's compiled debug classes.
# Build first: .\gradlew.bat assembleDebug. Usage: powershell -File tools\catchup-live-check\run.ps1
$ErrorActionPreference = 'Stop'
$root = Resolve-Path "$PSScriptRoot\..\.."
$jdk = 'C:\Program Files\Android\Android Studio\jbr\bin'
$classes = "$root\app\build\intermediates\javac\debug\compileDebugJavaWithJavac\classes"
$json = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.json\json" -Recurse -Filter json-*.jar | Sort-Object FullName -Descending | Select-Object -First 1).FullName
$sdk = ((Select-String -Path "$root\local.properties" -Pattern '^sdk.dir=').Line -replace '^sdk.dir=', '') -replace '\\\\', '\' -replace '\\:', ':'
$android = (Get-ChildItem "$sdk\platforms" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName + '\android.jar'
$recycler = (Get-ChildItem "$env:USERPROFILE\.gradle\caches" -Recurse -Filter classes.jar -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match '\\recyclerview-1\.[0-9.]+\\jars\\' } | Select-Object -First 1).FullName
$out = "$root\build\catchup-live-check"
New-Item -ItemType Directory -Force $out | Out-Null
$cp = "$classes;$json;$recycler;$android"
& "$jdk\javac.exe" -nowarn -cp $cp -d $out "$PSScriptRoot\com\astratv\app\CatchupLiveCheck.java"
& "$jdk\java.exe" -cp "$out;$cp" com.astratv.app.CatchupLiveCheck
