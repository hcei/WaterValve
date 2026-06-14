// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "WaterValveLogic",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "WaterValveLogic",
            targets: ["WaterValveLogic"]
        )
    ],
    targets: [
        .target(
            name: "WaterValveLogic",
            path: "WaterValve",
            sources: [
                "Core/APIClient.swift",
                "Core/AppConstants.swift",
                "Valve/ValveBridgeLogic.swift",
                "Background/BackgroundRefreshPolicy.swift",
                "Update/UpdateInfo.swift",
                "Update/UpdateRelease.swift",
                "Update/UpdateReleaseParser.swift",
                "Update/UpdateDecisionEngine.swift",
                "Update/UpdateService.swift"
            ]
        ),
        .testTarget(
            name: "WaterValveLogicTests",
            dependencies: ["WaterValveLogic"],
            path: "Tests/WaterValveLogicTests"
        )
    ]
)
