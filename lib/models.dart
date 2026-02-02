import 'package:json_annotation/json_annotation.dart';

part 'models.g.dart'; // This line is for code generation

@JsonSerializable()
class Threat {
  final String type;
  final String severity;
  final String description;

  Threat({
    required this.type,
    required this.severity,
    required this.description,
  });

  factory Threat.fromJson(Map<String, dynamic> json) => _$ThreatFromJson(json);
  Map<String, dynamic> toJson() => _$ThreatToJson(this);
}

@JsonSerializable()
class AppThreatInfo {
  final String packageName;
  final String appName;
  final bool isSystemApp;
  final List<Threat> detectedThreats; // Now a list of Threat objects
  final int zeroScore; // Overall threat score for the app

  AppThreatInfo({
    required this.packageName,
    required this.appName,
    required this.isSystemApp,
    required this.detectedThreats,
    this.zeroScore = 0, // Default to 0 if not provided
  });

  factory AppThreatInfo.fromJson(Map<String, dynamic> json) => _$AppThreatInfoFromJson(json);
  Map<String, dynamic> toJson() => _$AppThreatInfoToJson(this);
}

@JsonSerializable()
class ScanResultModel {
  final int totalInstalledPackages;
  final int suspiciousPackagesCount;
  final List<AppThreatInfo> threats;

  ScanResultModel({
    required this.totalInstalledPackages,
    required this.suspiciousPackagesCount,
    required this.threats,
  });

  factory ScanResultModel.fromJson(Map<String, dynamic> json) => _$ScanResultModelFromJson(json);
  Map<String, dynamic> toJson() => _$ScanResultModelToJson(this);
}