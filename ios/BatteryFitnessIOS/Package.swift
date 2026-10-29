// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "BatteryFitnessIOS",
    platforms: [
        .iOS(.v16),
        .macOS(.v13),
    ],
    products: [
        .library(
            name: "BatteryFitnessCore",
            targets: ["BatteryFitnessCore"]
        ),
        .library(
            name: "BatteryFitnessIOSApp",
            targets: ["BatteryFitnessIOSApp"]
        ),
    ],
    targets: [
        .target(
            name: "BatteryFitnessCore",
            dependencies: []
        ),
        .target(
            name: "BatteryFitnessIOSApp",
            dependencies: ["BatteryFitnessCore"]
        ),
        .testTarget(
            name: "BatteryFitnessCoreTests",
            dependencies: ["BatteryFitnessCore"]
        ),
    ]
)
