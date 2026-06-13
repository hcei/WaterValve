$ErrorActionPreference = "Stop"

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $root ".."))
$reportPath = Join-Path $root "logs\ios-static-validation.txt"

$checks = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param(
        [string]$Name,
        [bool]$Passed,
        [string]$Details
    )

    $checks.Add([PSCustomObject]@{
        Name = $Name
        Passed = $Passed
        Details = $Details
    }) | Out-Null
}

function Require-File {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required file missing: $Path"
    }

    return Get-Content -LiteralPath $Path -Raw
}

$projectPath = Join-Path $root "WaterValve.xcodeproj\project.pbxproj"
$plistPath = Join-Path $root "WaterValve\Resources\Info.plist"
$buildScriptPath = Join-Path $root "BuildPhases\build-shared.sh"
$sharedBuildBatPath = Join-Path $repoRoot "build_shared.bat"
$workflowPath = Join-Path $repoRoot ".github\workflows\ios-build.yml"
$settingsPath = Join-Path $repoRoot "settings.gradle.kts"
$progressPath = Join-Path $root "doc\tasks\progress.md"
$schemePath = Join-Path $root "WaterValve.xcodeproj\xcshareddata\xcschemes\WaterValve.xcscheme"
$sharedApiPath = Join-Path $repoRoot "shared\src\commonMain\kotlin\com\hgu\watervalve\shared\data\remote\api\UwcApi.kt"
$sharedSyncPath = Join-Path $repoRoot "shared\src\commonMain\kotlin\com\hgu\watervalve\shared\data\remote\api\SyncApi.kt"
$sharedReleasePath = Join-Path $repoRoot "shared\src\commonMain\kotlin\com\hgu\watervalve\shared\data\remote\api\ReleaseApi.kt"
$sharedCryptoPath = Join-Path $repoRoot "shared\src\commonMain\kotlin\com\hgu\watervalve\shared\data\remote\crypto\UwcCrypto.kt"
$sharedAuthRepoPath = Join-Path $repoRoot "shared\src\commonMain\kotlin\com\hgu\watervalve\shared\data\repository\AuthRepository.kt"
$sharedDeviceRepoPath = Join-Path $repoRoot "shared\src\commonMain\kotlin\com\hgu\watervalve\shared\data\repository\DeviceRepository.kt"
$sharedIosKeychainPath = Join-Path $repoRoot "shared\src\iosMain\kotlin\com\hgu\watervalve\shared\platform\KeychainWrapper.kt"
$sharedIosClockPath = Join-Path $repoRoot "shared\src\iosMain\kotlin\com\hgu\watervalve\shared\platform\PlatformClock.kt"
$sharedIosDefaultsPath = Join-Path $repoRoot "shared\src\iosMain\kotlin\com\hgu\watervalve\shared\platform\UserDefaultsWrapper.kt"
$sharedIosBridgePath = Join-Path $repoRoot "shared\src\iosMain\kotlin\com\hgu\watervalve\shared\platform\IosSharedBridge.kt"
$homeViewPath = Join-Path $root "WaterValve\UI\Home\HomeView.swift"
$valveViewPath = Join-Path $root "WaterValve\UI\Valve\ValveView.swift"
$webViewPath = Join-Path $root "WaterValve\UI\WebView\WebViewScreen.swift"
$bannedViewPath = Join-Path $root "WaterValve\UI\Common\BannedAlertView.swift"
$backgroundPath = Join-Path $root "WaterValve\Background\BackgroundTaskManager.swift"
$loginPath = Join-Path $root "WaterValve\UI\Login\LoginView.swift"
$sharedProbePath = Join-Path $root "WaterValve\Core\SharedBridgeProbe.swift"

$project = Require-File $projectPath
$plist = Require-File $plistPath
$buildScript = Require-File $buildScriptPath
$sharedBuildBat = Require-File $sharedBuildBatPath
$workflow = Require-File $workflowPath
$settings = Require-File $settingsPath
$progress = Require-File $progressPath
$scheme = Require-File $schemePath
$sharedApi = Require-File $sharedApiPath
$sharedSync = Require-File $sharedSyncPath
$sharedRelease = Require-File $sharedReleasePath
$sharedCrypto = Require-File $sharedCryptoPath
$sharedAuthRepo = Require-File $sharedAuthRepoPath
$sharedDeviceRepo = Require-File $sharedDeviceRepoPath
$sharedIosKeychain = Require-File $sharedIosKeychainPath
$sharedIosClock = Require-File $sharedIosClockPath
$sharedIosDefaults = Require-File $sharedIosDefaultsPath
$sharedIosBridge = Require-File $sharedIosBridgePath
$homeView = Require-File $homeViewPath
$valveView = Require-File $valveViewPath
$webView = Require-File $webViewPath
$bannedView = Require-File $bannedViewPath
$background = Require-File $backgroundPath
$loginView = Require-File $loginPath
$sharedProbe = Require-File $sharedProbePath

$requiredFiles = @(
    "WaterValve\WaterValveApp.swift",
    "WaterValve\Navigation\AppNavigation.swift",
    "WaterValve\Core\AppContainer.swift",
    "WaterValve\Core\AppState.swift",
    "WaterValve\Core\SharedAdapters.swift",
    "WaterValve\Core\SharedBridgeProbe.swift",
    "WaterValve\UI\Login\LoginView.swift",
    "WaterValve\UI\Home\HomeView.swift",
    "WaterValve\UI\QRScanner\QRScannerView.swift",
    "WaterValve\UI\Valve\ValveView.swift",
    "WaterValve\UI\Record\RecordView.swift",
    "WaterValve\UI\WebView\WebViewScreen.swift",
    "WaterValve\Update\UpdateAlertView.swift",
    "WaterValve\Background\BackgroundTaskManager.swift"
)

foreach ($relativePath in $requiredFiles) {
    $absolutePath = Join-Path $root $relativePath
    Add-Check "Required file exists: $relativePath" (Test-Path -LiteralPath $absolutePath) $absolutePath
}

$projectHasActiveSwiftSources =
    $project -match [regex]::Escape("WaterValveApp.swift") -and
    $project -match [regex]::Escape("BackgroundTaskManager.swift") -and
    $project -match [regex]::Escape("UpdateService.swift") -and
    $project -match [regex]::Escape("SharedAdapters.swift") -and
    $project -match [regex]::Escape("SharedBridgeProbe.swift")
Add-Check "Xcode project includes the active Swift app sources" $projectHasActiveSwiftSources "project.pbxproj should keep the current Swift target source set wired in."

$sharedProbeImportsModule =
    $sharedProbe -match [regex]::Escape("import Shared")
Add-Check "Swift target contains a concrete Shared framework import probe" $sharedProbeImportsModule "At least one Swift source should import Shared so the target verifies module availability during real Xcode builds."

$projectHasSharedBuildPhase =
    $project -match [regex]::Escape("PBXShellScriptBuildPhase") -and
    $project -match [regex]::Escape("Build Shared Framework") -and
    $project -match [regex]::Escape("BuildPhases/build-shared.sh")
Add-Check "Xcode project keeps the shared build shell phase wired in" $projectHasSharedBuildPhase "project.pbxproj should invoke ios/BuildPhases/build-shared.sh during Xcode builds."

$schemeTargetsNativeApp =
    $scheme -match [regex]::Escape('BlueprintIdentifier = "4F03D8852CFA000100000012"') -and
    $scheme -match [regex]::Escape('BuildableName = "WaterValve.app"')
Add-Check "Shared Xcode scheme points at the WaterValve native target" $schemeTargetsNativeApp "WaterValve.xcscheme should reference the PBXNativeTarget identifier instead of the product file reference."

$buildScriptBuildsShared =
    $buildScript -match [regex]::Escape(":shared:linkDebugFrameworkIosArm64") -and
    $buildScript -match [regex]::Escape(":shared:linkReleaseFrameworkIosArm64") -and
    $buildScript -match [regex]::Escape("FRAMEWORK_TASK_SUFFIX")
Add-Check "build-shared script selects shared framework tasks per Xcode configuration" $buildScriptBuildsShared "build-shared.sh should support both Debug and Release shared framework tasks."

$buildScriptInvokesGradleViaBash =
    $buildScript -match [regex]::Escape('bash ./gradlew "${TARGET_TASKS[@]}" --no-daemon')
Add-Check "build-shared script invokes Gradle via bash" $buildScriptInvokesGradleViaBash "build-shared.sh should call the Gradle wrapper through bash so macOS CI does not depend on the executable bit."

$projectConfiguresSharedFrameworkSearch =
    $project -match [regex]::Escape("FRAMEWORK_SEARCH_PATHS") -and
    $project -match [regex]::Escape("shared/build/bin/iosArm64/debugFramework") -and
    $project -match [regex]::Escape("shared/build/bin/iosArm64/releaseFramework") -and
    $project -match [regex]::Escape("shared/build/bin/iosSimulatorArm64/debugFramework") -and
    $project -match [regex]::Escape("shared/build/bin/iosSimulatorArm64/releaseFramework")
Add-Check "Xcode project declares shared framework search paths" $projectConfiguresSharedFrameworkSearch "project.pbxproj should expose Shared.framework search paths for device and simulator builds."

$buildSharedBatCompilesTests =
    $sharedBuildBat -match [regex]::Escape(":shared:compileTestKotlinJvm") -and
    $sharedBuildBat -match [regex]::Escape(":shared:jvmTest")
Add-Check "Windows shared validation script covers JVM test compilation and execution" $buildSharedBatCompilesTests "build_shared.bat should compile and run shared JVM tests in addition to generating SQLDelight code and compiling main sources."

$workflowInvokesBuildScriptSafely =
    $workflow -match [regex]::Escape("bash ./ios/BuildPhases/build-shared.sh")
Add-Check "Workflow invokes build-shared via bash" $workflowInvokesBuildScriptSafely "GitHub Actions should invoke build-shared.sh through bash so macOS runners do not depend on the executable bit."

$workflowInvokesGradleWrapperViaBash =
    $workflow -match [regex]::Escape("bash ./gradlew :shared:generateCommonMainWaterValveDbInterface :shared:compileKotlinJvm :shared:compileTestKotlinJvm :shared:jvmTest --no-daemon")
Add-Check "Workflow invokes the Gradle wrapper via bash" $workflowInvokesGradleWrapperViaBash "GitHub Actions should call the root Gradle wrapper through bash so macOS runners do not fail on a missing executable bit."

$settingsPreferOfficialReposOnCi =
    $settings -match [regex]::Escape('System.getenv("GITHUB_ACTIONS") != "true"') -and
    $settings -match [regex]::Escape("https://maven.aliyun.com/repository/gradle-plugin") -and
    $settings -match [regex]::Escape("gradlePluginPortal()")
Add-Check "Gradle settings skip China mirrors on GitHub Actions" $settingsPreferOfficialReposOnCi "GitHub Actions should resolve plugins from official repositories first to avoid stale mirror metadata for KSP and other Gradle plugins."

$workflowPinsXcodeSetup =
    $workflow -match [regex]::Escape("uses: maxim-lobanov/setup-xcode@v1") -and
    $workflow -match [regex]::Escape("xcode-version: latest-stable")
Add-Check "Workflow pins Xcode setup explicitly" $workflowPinsXcodeSetup "GitHub Actions should select Xcode explicitly instead of relying on the runner default."

$workflowRunsSharedJvmTests =
    $workflow -match [regex]::Escape(":shared:jvmTest")
Add-Check "Workflow runs shared JVM tests" $workflowRunsSharedJvmTests "GitHub Actions should execute shared JVM tests, not only compile them."

$hasCameraUsageDescription =
    $plist -match [regex]::Escape("NSCameraUsageDescription")
Add-Check "Info.plist keeps camera usage description" $hasCameraUsageDescription "Camera permission text is required for QR scanning."

$hasBgTaskIdentifier =
    $plist -match [regex]::Escape("BGTaskSchedulerPermittedIdentifiers") -and
    $plist -match [regex]::Escape("com.hgu.watervalve.tokenRefresh")
Add-Check "Info.plist keeps BG task identifier" $hasBgTaskIdentifier "Background refresh identifier must stay declared."

$hasBackgroundModes =
    $plist -match [regex]::Escape("UIBackgroundModes") -and
    $plist -match [regex]::Escape("<string>fetch</string>") -and
    $plist -match [regex]::Escape("<string>processing</string>")
Add-Check "Info.plist keeps background modes" $hasBackgroundModes "Background fetch and processing modes should be declared."

$bannedViewHasNoExit =
    $bannedView -notmatch [regex]::Escape("exit(0)")
Add-Check "Banned view does not force process exit" $bannedViewHasNoExit "Banned view should not terminate the app process directly."

$webViewHasScriptBridge =
    $webView -match [regex]::Escape("WKScriptMessageHandler") -and
    $webView -match [regex]::Escape("scriptMessageHandlerName") -and
    $webView -match [regex]::Escape("onScriptMessage")
Add-Check "WebView wrapper supports script-message bridge" $webViewHasScriptBridge "Valve/login flows rely on reusable WebKit bridge support."

$valveViewKeepsBridgeLogic =
    $valveView -match [regex]::Escape("@MainActor") -and
    $valveView -match [regex]::Escape("pendingScanCallback") -and
    $valveView -match [regex]::Escape("window.__valveBridge") -and
    $valveView -match [regex]::Escape("waterValveScanResult")
Add-Check "Valve view model is main-actor isolated and keeps callback bridge logic" $valveViewKeepsBridgeLogic "Valve page should keep both script-message and callback/event bridge paths."

$homeViewUsesDeleteConfirmation =
    $homeView -match [regex]::Escape("confirmationDialog") -and
    $homeView -match [regex]::Escape("pendingDeleteDevice")
Add-Check "Home view uses confirmation-based deletion" $homeViewUsesDeleteConfirmation "Device deletion should require confirmation."

$homeViewPreservesAddErrors =
    $homeView -match [regex]::Escape("addError") -and
    $homeView -match [regex]::Escape("let didSucceed = await container.addDevice")
Add-Check "Home add-device flow preserves errors instead of auto-closing on failure" $homeViewPreservesAddErrors "Add-device sheet should stay open on failure."

$backgroundHasReentryProtection =
    $background -match [regex]::Escape("isRefreshing") -and
    $background -match [regex]::Escape("guard !isRefreshing else")
Add-Check "Background manager has re-entry protection" $backgroundHasReentryProtection "Background refresh should avoid overlapping runs."

$backgroundRespectsSessionState =
    $background -match [regex]::Escape("func scheduleNextRefreshIfAuthorized()") -and
    $background -match [regex]::Escape("func cancelScheduledRefresh()") -and
    $background -match [regex]::Escape("cancel(taskRequestWithIdentifier: refreshIdentifier)")
Add-Check "Background manager can gate and cancel refresh requests by session state" $backgroundRespectsSessionState "Background refresh scheduling should respect login state and support logout cleanup."

$updateServiceAvoidsImpossibleForcedUpdate =
    (Require-File (Join-Path $root "WaterValve\Update\UpdateService.swift")) -match [regex]::Escape("let shouldForce = requiresUpgrade && hasDirectInstallable")
Add-Check "Update service avoids impossible forced updates for non-IPA releases" $updateServiceAvoidsImpossibleForcedUpdate "iOS should not hard-lock users on an Android-only release that has no IPA asset."

$swiftUpdateRepositoryHandlesProxyShape =
    (Require-File (Join-Path $root "WaterValve\Core\Repositories.swift")) -match [regex]::Escape('let releaseObject = (json["release"] as? [String: Any]) ?? json') -and
    (Require-File (Join-Path $root "WaterValve\Core\Repositories.swift")) -match [regex]::Escape('?? releaseObject["downloadUrl"] as? String')
Add-Check "Swift update repository tolerates proxy-wrapped release payloads" $swiftUpdateRepositoryHandlesProxyShape "The iOS update parser should handle both GitHub-style top-level release payloads and proxy-wrapped release objects."

$loginViewShowsStagedProgress =
    $loginView -match [regex]::Escape("@MainActor") -and
    $loginView -match [regex]::Escape("exchangeCasTicket(ticket: ticket)") -and
    $loginView -match [regex]::Escape("uiState = .loading(step: step, message: message)")
Add-Check "Login view model exposes staged progress through native exchange" $loginViewShowsStagedProgress "Login flow should surface staged native progress updates."

$progressTracksRepoScope =
    $progress -match [regex]::Escape("shared-api") -and
    $progress -match [regex]::Escape("ios-core") -and
    $progress -match [regex]::Escape("ci-cd")
Add-Check "Progress document tracks shared, ios, and ci modules together" $progressTracksRepoScope "progress.md should reflect the three in-scope delivery areas."

$sharedModulesExist =
    $sharedApi -match [regex]::Escape("class UwcApi") -and
    $sharedSync -match [regex]::Escape("class SyncApi") -and
    $sharedRelease -match [regex]::Escape("class ReleaseApi") -and
    $sharedAuthRepo -match [regex]::Escape("class AuthRepository") -and
    $sharedDeviceRepo -match [regex]::Escape("class DeviceRepository")
Add-Check "Shared API and repository modules exist" $sharedModulesExist "The KMP shared layer should define API and repository classes under shared/commonMain."

$sharedCryptoAvoidsJvmOnlyApis =
    $sharedCrypto -notmatch [regex]::Escape("toSortedMap()") -and
    $sharedCrypto -notmatch [regex]::Escape("Integer.rotateLeft") -and
    $sharedCrypto -notmatch [regex]::Escape('"%02x".format(')
Add-Check "Shared crypto avoids JVM-only helpers in commonMain" $sharedCryptoAvoidsJvmOnlyApis "Kotlin/Native builds should not depend on JVM-only APIs such as toSortedMap, Integer.rotateLeft, or String.format in commonMain crypto code."

$sharedIosPlatformUsesNativeSafeApis =
    $sharedIosKeychain -match "ExperimentalForeignApi::class" -and
    $sharedIosKeychain -match [regex]::Escape("CFDictionaryCreate") -and
    $sharedIosKeychain -match [regex]::Escape("CFBridgingRetain") -and
    $sharedIosKeychain -match [regex]::Escape("CFBridgingRelease") -and
    $sharedIosBridge -match [regex]::Escape("class IosSharedBridge") -and
    $sharedIosBridge -match [regex]::Escape("HttpClient(Darwin)") -and
    $sharedIosBridge -match [regex]::Escape("WaterValveDb(DatabaseDriverFactory().createDriver())") -and
    $sharedIosClock -match [regex]::Escape("time(null)") -and
    $sharedIosDefaults -match [regex]::Escape("objectForKey") -and
    $sharedIosDefaults -notmatch [regex]::Escape("stringForKey")
Add-Check "Shared iosMain platform wrappers use Kotlin/Native-safe interop" $sharedIosPlatformUsesNativeSafeApis "The iosMain keychain, clock, and defaults wrappers should use Kotlin/Native-safe Foundation/Security interop patterns."

$total = $checks.Count
$passed = ($checks | Where-Object { $_.Passed }).Count
$failed = $total - $passed

$reportLines = @()
$reportLines += "WaterValve iOS Static Validation"
$reportLines += "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$reportLines += "Root: $root"
$reportLines += "Passed: $passed / $total"
$reportLines += ""

foreach ($check in $checks) {
    $status = if ($check.Passed) { "PASS" } else { "FAIL" }
    $reportLines += "[$status] $($check.Name)"
    $reportLines += "        $($check.Details)"
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $reportPath) | Out-Null
Set-Content -LiteralPath $reportPath -Value $reportLines -Encoding UTF8
$reportLines -join [Environment]::NewLine

if ($failed -gt 0) {
    exit 1
}
