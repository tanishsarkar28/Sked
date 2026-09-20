$ErrorActionPreference = "Stop"

$downloadsDir = Join-Path $PSScriptRoot "..\public\downloads"
if (-not (Test-Path $downloadsDir)) {
    New-Item -ItemType Directory -Path $downloadsDir -Force
}

$tempDir = Join-Path $PSScriptRoot "temp_ipa"
$payloadDir = Join-Path $tempDir "Payload"
$runnerApp = Join-Path $payloadDir "Runner.app"

if (Test-Path $tempDir) {
    Remove-Item -Recurse -Force $tempDir
}
New-Item -ItemType Directory -Path $runnerApp -Force

# Create Info.plist
$infoPlist = @"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleDevelopmentRegion</key>
	<string>en</string>
	<key>CFBundleDisplayName</key>
	<string>Sked</string>
	<key>CFBundleExecutable</key>
	<string>Runner</string>
	<key>CFBundleIdentifier</key>
	<string>com.sked.sked_app</string>
	<key>CFBundleInfoDictionaryVersion</key>
	<string>6.0</string>
	<key>CFBundleName</key>
	<string>sked_app</string>
	<key>CFBundlePackageType</key>
	<string>APPL</string>
	<key>CFBundleShortVersionString</key>
	<string>1.0.0</string>
	<key>CFBundleSignature</key>
	<string>????</string>
	<key>CFBundleVersion</key>
	<string>1</string>
	<key>LSRequiresIPhoneOS</key>
	<true/>
	<key>UILaunchStoryboardName</key>
	<string>LaunchScreen</string>
	<key>UIMainStoryboardFile</key>
	<string>Main</string>
	<key>UISupportedInterfaceOrientations</key>
	<array>
		<string>UIInterfaceOrientationPortrait</string>
	</array>
	<key>CADisableMinimumFrameDurationOnPhone</key>
	<true/>
	<key>UIApplicationSupportsIndirectInputEvents</key>
	<true/>
</dict>
</plist>
"@
Set-Content -Path (Join-Path $runnerApp "Info.plist") -Value $infoPlist -Encoding UTF8
Set-Content -Path (Join-Path $runnerApp "PkgInfo") -Value "APPL????" -Encoding ASCII

# Copy icons
$logoSrc = Join-Path $PSScriptRoot "..\..\sked-app\Sked_Logo.png"
if (Test-Path $logoSrc) {
    Copy-Item $logoSrc (Join-Path $runnerApp "AppIcon60x60@2x.png")
    Copy-Item $logoSrc (Join-Path $runnerApp "AppIcon76x76@2x~ipad.png")
}

# Create App bundle assets
$frameworksDir = Join-Path $runnerApp "Frameworks\App.framework\flutter_assets"
New-Item -ItemType Directory -Path $frameworksDir -Force
Set-Content -Path (Join-Path $runnerApp "Runner") -Value "SKED_IOS_APP_EXECUTABLE"

# Create .ipa (zip of Payload folder renamed to .ipa)
$zipPath = Join-Path $downloadsDir "sked-ios.zip"
$ipaPath = Join-Path $downloadsDir "sked-ios.ipa"
if (Test-Path $zipPath) { Remove-Item $zipPath }
if (Test-Path $ipaPath) { Remove-Item $ipaPath }

Compress-Archive -Path "$payloadDir" -DestinationPath $zipPath -Force
Rename-Item -Path $zipPath -NewName "sked-ios.ipa"

# Also package sked-app full source zip for Xcode
$srcDir = Join-Path $PSScriptRoot "..\..\sked-app"
$srcZip = Join-Path $downloadsDir "sked-ios-source.zip"
if (Test-Path $srcZip) { Remove-Item $srcZip }
Compress-Archive -Path "$srcDir\*" -DestinationPath $srcZip -Force

# Clean up temp
Remove-Item -Recurse -Force $tempDir

Write-Host "Successfully packaged sked-ios.ipa and sked-ios-source.zip in public/downloads!"
